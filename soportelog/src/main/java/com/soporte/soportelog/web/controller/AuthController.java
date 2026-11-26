package com.soporte.soportelog.web.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.soporte.soportelog.persistence.entity.UserEntity;
import com.soporte.soportelog.service.OtpService;
import com.soporte.soportelog.service.SessionService;
import com.soporte.soportelog.service.dto.AuthTokensDto;
import com.soporte.soportelog.service.dto.NewSessionDto;
import com.soporte.soportelog.service.dto.OtpRequestDto;
import com.soporte.soportelog.service.dto.OtpVerifyDto;
import com.soporte.soportelog.service.dto.RefreshRequestDto;
import com.soporte.soportelog.service.dto.RefreshedSessionDto;
import com.soporte.soportelog.service.exception.InvalidOtpException;
import com.soporte.soportelog.service.exception.InvalidSessionException;
import com.soporte.soportelog.service.exception.OtpCooldownException;
import com.soporte.soportelog.service.exception.OtpDeliveryException;
import com.soporte.soportelog.web.config.JwtUtil;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final OtpService otpService;
    private final SessionService sessionService;
    private final JwtUtil jwtUtil;

    @Autowired
    public AuthController(OtpService otpService, SessionService sessionService, JwtUtil jwtUtil) {
        this.otpService = otpService;
        this.sessionService = sessionService;
        this.jwtUtil = jwtUtil;
    }

    // Paso 1: el usuario escribe su correo y se le envia un codigo
    @PostMapping("/otp/request")
    public ResponseEntity<?> requestOtp(@Valid @RequestBody OtpRequestDto otpRequestDto) {
        try {
            return ResponseEntity.ok(this.otpService.requestCode(otpRequestDto.getEmail()));
        } catch (OtpCooldownException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
                    .body(Map.of("message", e.getMessage(), "retryAfterSeconds", e.getRetryAfterSeconds()));
        } catch (OtpDeliveryException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", e.getMessage()));
        }
    }

    // Paso 2: el usuario escribe el codigo y, si es correcto, se abre una sesion
    // para ese dispositivo y recibe sus dos tokens
    @PostMapping("/otp/verify")
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody OtpVerifyDto otpVerifyDto) {
        try {
            UserEntity user = this.otpService.verifyCode(otpVerifyDto.getEmail(), otpVerifyDto.getCode());
            NewSessionDto session = this.sessionService.create(user, otpVerifyDto.getDeviceName());
            String accessToken = this.jwtUtil.create(user.getEmail(), session.sessionId());
            return ResponseEntity.ok(new AuthTokensDto(accessToken, session.refreshToken(),
                    this.jwtUtil.getExpirationSeconds()));
        } catch (InvalidOtpException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        }
    }

    // Cuando el token de acceso caduca, el dispositivo pide otro con su token de
    // renovacion, sin que el usuario tenga que escribir ningun codigo
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshRequestDto refreshRequestDto) {
        try {
            RefreshedSessionDto session = this.sessionService.refresh(refreshRequestDto.getRefreshToken());
            String accessToken = this.jwtUtil.create(session.email(), session.sessionId());
            return ResponseEntity.ok(new AuthTokensDto(accessToken, session.refreshToken(),
                    this.jwtUtil.getExpirationSeconds()));
        } catch (InvalidSessionException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        }
    }

    // Cierra la sesion del dispositivo que hace la peticion. El filtro JWT dejo
    // anotado en "details" de que sesion viene el token
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {
        this.sessionService.revoke((Long) authentication.getDetails());
        return ResponseEntity.noContent().build();
    }
}
