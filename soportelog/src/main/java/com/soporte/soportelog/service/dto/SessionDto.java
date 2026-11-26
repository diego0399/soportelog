package com.soporte.soportelog.service.dto;

import java.time.Instant;

/*
 * Lo que se muestra de una sesion en la lista de dispositivos. No incluye el
 * hash del token: ese dato nunca sale del servidor.
 */
public record SessionDto(Long id, String deviceName, Instant createdAt, Instant lastUsedAt, boolean current) {
}
