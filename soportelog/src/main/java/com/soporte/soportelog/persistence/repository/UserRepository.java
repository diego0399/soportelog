package com.soporte.soportelog.persistence.repository;

import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

import com.soporte.soportelog.persistence.entity.UserEntity;

public interface UserRepository extends CrudRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmail(String email);
}
