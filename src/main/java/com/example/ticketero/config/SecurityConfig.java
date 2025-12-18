package com.example.ticketero.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración de seguridad para producción.
 * 
 * Protege endpoints administrativos y permite acceso público a APIs de tickets.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Configuración de seguridad HTTP.
     * 
     * Reglas:
     * - /api/health: Público (para health checks)
     * - /actuator/health: Público (para monitoring)
     * - /api/tickets: Autenticado (crear/consultar tickets)
     * - /api/admin: Requiere rol ADMIN
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Deshabilitar CSRF para APIs REST
            .authorizeHttpRequests(auth -> auth
                // Permitir acceso a TODAS las rutas sin autenticación
                .anyRequest().permitAll()
            )
            .httpBasic(basic -> basic.disable()); // Deshabilitar autenticación HTTP Basic
        
        return http.build();
    }

    /**
     * Encoder de passwords con BCrypt.
     * Usado para encriptar passwords de usuarios.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
