package com.soporte.soportelog.web.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.soporte.soportelog.service.SessionService;
import com.soporte.soportelog.service.dto.SessionDto;

@RestController
@RequestMapping("/api/auth/sessions")
public class SessionController {
    private final SessionService sessionService;

    @Autowired
    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    // Paso 5: los dispositivos donde el usuario tiene la sesion abierta
    @GetMapping
    public ResponseEntity<List<SessionDto>> list(Authentication authentication) {
        return ResponseEntity.ok(this.sessionService.listOpen(
                authentication.getName(),
                (Long) authentication.getDetails()));
    }

    // Cierra una de sus sesiones desde cualquier dispositivo
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> close(@PathVariable Long id, Authentication authentication) {
        boolean closed = this.sessionService.revokeOwned(id, authentication.getName());
        return closed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
