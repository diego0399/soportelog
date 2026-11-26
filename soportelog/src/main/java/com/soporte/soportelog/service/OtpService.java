package com.soporte.soportelog.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soporte.soportelog.persistence.entity.OtpCodeEntity;
import com.soporte.soportelog.persistence.entity.UserEntity;
import com.soporte.soportelog.persistence.repository.OtpCodeRepository;
import com.soporte.soportelog.service.dto.OtpSentDto;
import com.soporte.soportelog.service.exception.InvalidOtpException;
import com.soporte.soportelog.service.exception.OtpCooldownException;

@Service
public class OtpService {
    // SecureRandom y no Random: el codigo no debe poder predecirse
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpCodeRepository otpCodeRepository;
    private final UserService userService;
    private final OtpSender otpSender;
    private final PasswordEncoder passwordEncoder;
    private final long expirationMinutes;
    private final int maxAttempts;
    private final long resendSeconds;

    @Autowired
    public OtpService(OtpCodeRepository otpCodeRepository, UserService userService, OtpSender otpSender,
            PasswordEncoder passwordEncoder,
            @Value("${otp.expiration-minutes}") long expirationMinutes,
            @Value("${otp.max-attempts}") int maxAttempts,
            @Value("${otp.resend-seconds}") long resendSeconds) {
        this.otpCodeRepository = otpCodeRepository;
        this.userService = userService;
        this.otpSender = otpSender;
        this.passwordEncoder = passwordEncoder;
        this.expirationMinutes = expirationMinutes;
        this.maxAttempts = maxAttempts;
        this.resendSeconds = resendSeconds;
    }

    /*
     * Paso 1 del login: genera un codigo de 6 digitos, guarda su hash y lo envia.
     * Es transaccional: si el envio falla, no queda guardado un codigo que el
     * usuario nunca recibio.
     */
    @Transactional
    public OtpSentDto requestCode(String rawEmail) {
        String email = normalize(rawEmail);
        Instant now = Instant.now();

        // Hay que esperar un tiempo entre un codigo y el siguiente
        Optional<OtpCodeEntity> last = this.otpCodeRepository.findFirstByEmailOrderByCreatedAtDesc(email);
        if (last.isPresent()) {
            long elapsed = Duration.between(last.get().getCreatedAt(), now).getSeconds();
            if (elapsed < this.resendSeconds) {
                throw new OtpCooldownException(this.resendSeconds - elapsed);
            }
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));

        // Un codigo nuevo invalida los anteriores de ese correo
        this.otpCodeRepository.deleteAllByEmail(email);

        OtpCodeEntity otp = new OtpCodeEntity();
        otp.setEmail(email);
        otp.setCodeHash(this.passwordEncoder.encode(code));
        otp.setExpiresAt(now.plus(Duration.ofMinutes(this.expirationMinutes)));
        otp.setAttempts(0);
        otp.setUsed(false);
        otp.setCreatedAt(now);
        this.otpCodeRepository.save(otp);

        this.otpSender.send(email, code);

        return new OtpSentDto(this.expirationMinutes * 60, this.resendSeconds);
    }

    /*
     * Paso 2 del login: comprueba el codigo y devuelve el usuario (lo crea si es
     * su primera vez). No es transaccional a proposito: el intento gastado debe
     * quedar guardado aunque despues se lance InvalidOtpException.
     */
    public UserEntity verifyCode(String rawEmail, String code) {
        String email = normalize(rawEmail);
        OtpCodeEntity otp = this.otpCodeRepository.findFirstByEmailOrderByCreatedAtDesc(email)
                .orElseThrow(InvalidOtpException::new);

        if (otp.getUsed() || otp.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidOtpException();
        }

        // El intento se cuenta ANTES de comparar; si ya no quedan, se rechaza
        if (this.otpCodeRepository.registerAttempt(otp.getId(), this.maxAttempts) == 0) {
            throw new InvalidOtpException();
        }

        if (!this.passwordEncoder.matches(code, otp.getCodeHash())) {
            throw new InvalidOtpException();
        }

        // El codigo queda gastado: no se puede volver a usar
        if (this.otpCodeRepository.markAsUsed(otp.getId()) == 0) {
            throw new InvalidOtpException();
        }

        return this.userService.findOrCreateByEmail(email);
    }

    // "Diego@Gmail.com" y "diego@gmail.com" son el mismo usuario
    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
