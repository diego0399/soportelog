package com.soporte.soportelog.web.config;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.soporte.soportelog.service.SessionService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final SessionService sessionService;

    @Autowired
    public JwtFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService, SessionService sessionService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.sessionService = sessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        // Sin token: la peticion sigue como anonima y SecurityConfig decide si pasa
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring("Bearer ".length()).trim();

        if (!this.jwtUtil.isValid(jwt)) {
            filterChain.doFilter(request, response);
            return;
        }

        // El token puede estar bien firmado y sin caducar, pero venir de una sesion
        // que ya se cerro: en ese caso no vale
        Long sessionId = this.jwtUtil.getSessionId(jwt);
        if (sessionId == null || !this.sessionService.isActive(sessionId)) {
            filterChain.doFilter(request, response);
            return;
        }

        String email = this.jwtUtil.getEmail(jwt);
        UserDetails user;
        try {
            user = this.userDetailsService.loadUserByUsername(email);
        } catch (UsernameNotFoundException e) {
            // El token es valido pero el usuario ya no existe
            filterChain.doFilter(request, response);
            return;
        }

        if (!user.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                user.getUsername(),
                null,
                user.getAuthorities());
        // Se guarda desde que sesion llega la peticion; los controladores lo leen
        // con authentication.getDetails()
        authenticationToken.setDetails(sessionId);
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        filterChain.doFilter(request, response);
    }

}
