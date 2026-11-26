package com.soporte.soportelog.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soporte.soportelog.persistence.entity.SessionEntity;
import com.soporte.soportelog.persistence.entity.UserEntity;
import com.soporte.soportelog.persistence.repository.SessionRepository;
import com.soporte.soportelog.service.dto.NewSessionDto;
import com.soporte.soportelog.service.dto.RefreshedSessionDto;
import com.soporte.soportelog.service.dto.SessionDto;
import com.soporte.soportelog.service.exception.InvalidSessionException;

@Service
public class SessionService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SessionRepository sessionRepository;
    private final long inactivityDays;

    @Autowired
    public SessionService(SessionRepository sessionRepository,
            @Value("${session.inactivity-days}") long inactivityDays) {
        this.sessionRepository = sessionRepository;
        this.inactivityDays = inactivityDays;
    }

    /*
     * Abre una sesion para un dispositivo. Devuelve el token de renovacion en
     * claro: es la unica vez que existe fuera del dispositivo, porque en la base
     * solo queda su hash.
     */
    public NewSessionDto create(UserEntity user, String deviceName) {
        String refreshToken = this.generateToken();
        Instant now = Instant.now();

        SessionEntity session = new SessionEntity();
        session.setUser(user);
        session.setRefreshTokenHash(this.hash(refreshToken));
        session.setDeviceName(deviceName == null || deviceName.isBlank() ? null : deviceName.trim());
        session.setRevoked(false);
        session.setCreatedAt(now);
        session.setLastUsedAt(now);
        session = this.sessionRepository.save(session);

        return new NewSessionDto(session.getId(), refreshToken);
    }

    /*
     * Comprueba que el token de renovacion pertenece a una sesion viva y anota
     * que el dispositivo sigue activo. Es transaccional porque lee el usuario de
     * la sesion, que se carga de la base solo cuando se pide (LAZY).
     */
    @Transactional
    public RefreshedSessionDto refresh(String refreshToken) {
        SessionEntity session = this.sessionRepository.findByRefreshTokenHash(this.hash(refreshToken))
                .orElseThrow(InvalidSessionException::new);

        Instant now = Instant.now();
        boolean inactive = session.getLastUsedAt().plus(Duration.ofDays(this.inactivityDays)).isBefore(now);

        if (session.getRevoked() || inactive || session.getUser().getDisabled()) {
            throw new InvalidSessionException();
        }

        session.setLastUsedAt(now);
        this.sessionRepository.save(session);

        return new RefreshedSessionDto(session.getId(), session.getUser().getEmail(), refreshToken);
    }

    // Lo consulta el filtro de seguridad en cada peticion
    public boolean isActive(Long sessionId) {
        return this.sessionRepository.existsByIdAndRevokedFalse(sessionId);
    }

    // Cierra una sesion. La fila se conserva, marcada como revocada
    public void revoke(Long sessionId) {
        this.sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setRevoked(true);
            this.sessionRepository.save(session);
        });
    }

    // Los dispositivos con sesion abierta de un usuario. "current" marca el que pregunta
    public List<SessionDto> listOpen(String email, Long currentSessionId) {
        return this.sessionRepository.findByUserEmailAndRevokedFalseOrderByLastUsedAtDesc(email).stream()
                .map(session -> new SessionDto(
                        session.getId(),
                        session.getDeviceName(),
                        session.getCreatedAt(),
                        session.getLastUsedAt(),
                        session.getId().equals(currentSessionId)))
                .toList();
    }

    /*
     * Cierra una sesion elegida por su id, pero solo si es del usuario que lo
     * pide. Devuelve false si no existe, ya estaba cerrada o es de otra persona;
     * desde fuera los tres casos se ven igual.
     */
    public boolean revokeOwned(Long sessionId, String email) {
        Optional<SessionEntity> session = this.sessionRepository.findByIdAndUserEmailAndRevokedFalse(sessionId, email);
        if (session.isEmpty()) {
            return false;
        }
        session.get().setRevoked(true);
        this.sessionRepository.save(session.get());
        return true;
    }

    // 32 bytes aleatorios (256 bits) escritos como texto: imposible de adivinar
    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // SHA-256 en hexadecimal: siempre 64 caracteres y siempre igual para el mismo token
    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
