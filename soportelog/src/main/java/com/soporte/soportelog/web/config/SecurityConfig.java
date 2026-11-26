package com.soporte.soportelog.web.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {
    private final JwtFilter jwtFilter;
    private final List<String> allowedOrigins;

    @Autowired
    public SecurityConfig(JwtFilter jwtFilter, @Value("${cors.allowed-origins:}") List<String> allowedOrigins) {
        this.jwtFilter = jwtFilter;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Usa el bean corsConfigurationSource de abajo. Va dentro de Spring
                // Security para que la peticion previa (OPTIONS) se responda antes
                // de exigir el JWT
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Pedir el codigo, verificarlo y renovar el token es publico (todavia
                        // no hay token de acceso valido). El resto, incluido cerrar
                        // sesion, exige JWT
                        .requestMatchers("/api/auth/otp/**", "/api/auth/refresh", "/error").permitAll()
                        .anyRequest()
                        .authenticated())
                // Sin token valido se responde 401 en lugar del 403 por defecto
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /*
     * CORS: que paginas web (origenes) pueden llamar a esta API desde el navegador.
     * Solo afecta a navegadores (Flutter Web, React...); las apps de celular y
     * Postman no lo necesitan. Los origenes salen de cors.allowed-origins.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // "Patterns" admite comodines como http://localhost:* (el puerto de Flutter
        // Web cambia en cada ejecucion)
        config.setAllowedOriginPatterns(this.allowedOrigins.stream().map(String::trim)
                .filter(origin -> !origin.isEmpty()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // Sin esto, el navegador oculta la cabecera Retry-After de la respuesta 429
        config.setExposedHeaders(List.of("Retry-After"));
        // El JWT viaja en la cabecera Authorization, no en cookies
        config.setAllowCredentials(false);
        // El navegador recuerda la respuesta previa 1 hora y no la repite en cada peticion
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    // Se usa para guardar el hash de los codigos OTP
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
