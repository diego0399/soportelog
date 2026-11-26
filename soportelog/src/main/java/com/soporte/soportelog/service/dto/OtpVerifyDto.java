package com.soporte.soportelog.service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OtpVerifyDto {
    @NotBlank
    @Email(message = "El correo no tiene un formato valido")
    @Size(max = 100)
    private String email;

    @NotBlank
    @Pattern(regexp = "^\\d{6}$", message = "El codigo debe tener 6 digitos")
    private String code;

    // Opcional: nombre del dispositivo, para reconocerlo en la lista de sesiones
    @Size(max = 100)
    private String deviceName;
}
