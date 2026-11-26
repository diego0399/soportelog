package com.soporte.soportelog.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RefreshRequestDto {
    @NotBlank
    @Size(max = 200)
    private String refreshToken;
}
