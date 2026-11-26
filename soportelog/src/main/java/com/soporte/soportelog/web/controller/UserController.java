package com.soporte.soportelog.web.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.soporte.soportelog.service.UserService;
import com.soporte.soportelog.service.dto.UserDto;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    // Endpoint protegido: devuelve el usuario dueno del JWT
    @GetMapping("/me")
    public ResponseEntity<UserDto> me(Authentication authentication) {
        return this.userService.findByEmail(authentication.getName())
                .map(user -> ResponseEntity.ok(new UserDto(user.getId(), user.getEmail(), user.getCreatedAt())))
                .orElse(ResponseEntity.notFound().build());
    }
}
