package com.soporte.soportelog.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.soporte.soportelog.persistence.entity.UserEntity;
import com.soporte.soportelog.persistence.repository.UserRepository;

@Service
public class UserSecurityService implements UserDetailsService {
    private final UserRepository userRepository;

    @Autowired
    public UserSecurityService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Aqui el "username" de Spring Security es el correo
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserEntity userEntity = this.userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User " + email + " not found."));

        return User.builder()
                .username(userEntity.getEmail())
                .password("") // no hay contrasena: el acceso es solo por codigo OTP
                .roles("USER")
                .disabled(userEntity.getDisabled())
                .build();
    }
}
