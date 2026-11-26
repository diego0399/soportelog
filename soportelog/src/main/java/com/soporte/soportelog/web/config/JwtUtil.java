package com.soporte.soportelog.web.config;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

@Component
public class JwtUtil {
    private static final String ISSUER = "soportelog";

    private final Algorithm algorithm;
    private final long accessMinutes;

    // La clave viene de la configuracion (jwt.secret), no va escrita en el codigo
    @Autowired
    public JwtUtil(@Value("${jwt.secret}") String secret, @Value("${jwt.access-minutes}") long accessMinutes) {
        this.algorithm = Algorithm.HMAC256(secret);
        this.accessMinutes = accessMinutes;
    }

    public long getExpirationSeconds() {
        return TimeUnit.MINUTES.toSeconds(this.accessMinutes);
    }

    // El token lleva quien es (correo) y desde que sesion/dispositivo entro ("sid")
    public String create(String email, Long sessionId) {
        return JWT.create()
                .withSubject(email)
                .withClaim("sid", sessionId)
                .withIssuer(ISSUER)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(this.accessMinutes)))
                .sign(this.algorithm);
    }

    public boolean isValid(String jwt) {
        try {
            JWT.require(this.algorithm)
                    .withIssuer(ISSUER)
                    .build()
                    .verify(jwt);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Devuelve null si el token no trae sesion
    public Long getSessionId(String jwt) {
        return JWT.require(this.algorithm)
                .withIssuer(ISSUER)
                .build()
                .verify(jwt)
                .getClaim("sid")
                .asLong();
    }

    public String getEmail(String jwt) {
        return JWT.require(this.algorithm)
                .withIssuer(ISSUER)
                .build()
                .verify(jwt)
                .getSubject();
    }
}
