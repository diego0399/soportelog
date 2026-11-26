package com.soporte.soportelog.service.dto;

import java.time.Instant;

public record UserDto(Long id, String email, Instant createdAt) {
}
