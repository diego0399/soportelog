package com.soporte.soportelog.service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OtpRequestDto {
    // @Email comprueba que tenga forma de correo (algo@dominio)
    @NotBlank
    @Email(message = "El correo no tiene un formato valido")
    @Size(max = 100)
    private String email;
}
