package com.soporte.soportelog.persistence.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.soporte.soportelog.persistence.entity.OtpCodeEntity;

public interface OtpCodeRepository extends CrudRepository<OtpCodeEntity, Long> {

    Optional<OtpCodeEntity> findFirstByEmailOrderByCreatedAtDesc(String email);

    @Transactional
    @Modifying
    @Query("DELETE FROM OtpCodeEntity o WHERE o.email = :email")
    void deleteAllByEmail(@Param("email") String email);

    /*
     * Gasta un intento directamente en la base de datos. Devuelve 1 si quedaba
     * algun intento y 0 si ya no. Al ser un solo UPDATE, dos peticiones a la vez
     * no pueden saltarse el limite de intentos.
     */
    @Transactional
    @Modifying
    @Query("UPDATE OtpCodeEntity o SET o.attempts = o.attempts + 1 "
            + "WHERE o.id = :id AND o.used = false AND o.attempts < :maxAttempts")
    int registerAttempt(@Param("id") Long id, @Param("maxAttempts") int maxAttempts);

    /*
     * Marca el codigo como usado. Devuelve 1 solo a la primera peticion que lo
     * consigue, asi un mismo codigo nunca inicia dos sesiones.
     */
    @Transactional
    @Modifying
    @Query("UPDATE OtpCodeEntity o SET o.used = true WHERE o.id = :id AND o.used = false")
    int markAsUsed(@Param("id") Long id);
}
