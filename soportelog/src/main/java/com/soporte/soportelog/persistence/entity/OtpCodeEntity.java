package com.soporte.soportelog.persistence.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "otp_codes", indexes = @Index(name = "idx_otp_codes_email", columnList = "email"))
@Getter
@Setter
@NoArgsConstructor
public class OtpCodeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Correo al que se envio el codigo
    @Column(nullable = false, length = 100)
    private String email;

    // Nunca se guarda el codigo en claro, solo su hash (BCrypt)
    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    // Intentos de verificacion gastados sobre este codigo
    @Column(nullable = false)
    private Integer attempts;

    // Un codigo solo sirve una vez
    @Column(nullable = false)
    private Boolean used;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
