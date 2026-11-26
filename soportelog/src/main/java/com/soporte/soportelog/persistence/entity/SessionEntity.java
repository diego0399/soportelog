package com.soporte.soportelog.persistence.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Una fila por cada dispositivo con la sesion abierta
@Entity
@Table(name = "sessions", indexes = @Index(name = "idx_sessions_user_id", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
public class SessionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Muchos dispositivos (sesiones) pueden pertenecer a un mismo usuario
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    // Hash SHA-256 del token de renovacion; el token real solo lo tiene el dispositivo
    @Column(name = "refresh_token_hash", nullable = false, unique = true, length = 64)
    private String refreshTokenHash;

    // Nombre que envia el dispositivo ("Laptop de Diego") para reconocerlo en la lista
    @Column(name = "device_name", length = 100)
    private String deviceName;

    // true = sesion cerrada; la fila se conserva pero ya no sirve
    @Column(nullable = false)
    private Boolean revoked;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Se actualiza cada vez que el dispositivo renueva su token
    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;
}
