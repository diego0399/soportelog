package com.soporte.soportelog.service.dto;

// Resultado de renovar: la sesion, de quien es (correo) y su token de renovacion
public record RefreshedSessionDto(Long sessionId, String email, String refreshToken) {
}
