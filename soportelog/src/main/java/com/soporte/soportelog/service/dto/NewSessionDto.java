package com.soporte.soportelog.service.dto;

// Resultado de abrir una sesion: su id y el token de renovacion en claro
public record NewSessionDto(Long sessionId, String refreshToken) {
}
