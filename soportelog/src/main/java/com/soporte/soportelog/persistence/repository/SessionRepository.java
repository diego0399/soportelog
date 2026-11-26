package com.soporte.soportelog.persistence.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

import com.soporte.soportelog.persistence.entity.SessionEntity;

public interface SessionRepository extends CrudRepository<SessionEntity, Long> {

    // Encuentra la sesion a la que pertenece un token de renovacion
    Optional<SessionEntity> findByRefreshTokenHash(String refreshTokenHash);

    // true si la sesion existe y no esta cerrada
    boolean existsByIdAndRevokedFalse(Long id);

    // Las sesiones abiertas de un usuario (por su correo), la mas reciente primero
    List<SessionEntity> findByUserEmailAndRevokedFalseOrderByLastUsedAtDesc(String email);

    // Una sesion abierta concreta, pero solo si pertenece a ese usuario
    Optional<SessionEntity> findByIdAndUserEmailAndRevokedFalse(Long id, String email);
}
