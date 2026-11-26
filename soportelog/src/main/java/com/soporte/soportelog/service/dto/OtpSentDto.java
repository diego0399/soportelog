package com.soporte.soportelog.service.dto;

// Respuesta al pedir un codigo: cuanto dura y cuando se puede pedir otro
public record OtpSentDto(long expiresInSeconds, long resendInSeconds) {
}
