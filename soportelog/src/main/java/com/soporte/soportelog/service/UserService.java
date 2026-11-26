package com.soporte.soportelog.service;

import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.soporte.soportelog.persistence.entity.UserEntity;
import com.soporte.soportelog.persistence.repository.UserRepository;

@Service
public class UserService {
    private final UserRepository userRepository;

    @Autowired
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<UserEntity> findByEmail(String email) {
        return this.userRepository.findByEmail(email);
    }

    // La primera vez que un correo se verifica, se registra
    public UserEntity findOrCreateByEmail(String email) {
        return this.userRepository.findByEmail(email).orElseGet(() -> {
            UserEntity user = new UserEntity();
            user.setEmail(email);
            user.setDisabled(false);
            user.setCreatedAt(Instant.now());
            return this.userRepository.save(user);
        });
    }
}
