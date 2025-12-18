package com.example.ticketero.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Configuración de logging estructurado con MDC
 */
@Configuration
@Slf4j
public class LoggingConfig {

    private static final org.slf4j.Logger AUDIT_LOGGER = LoggerFactory.getLogger("AUDIT");

    @Bean
    public OncePerRequestFilter loggingFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, 
                                          HttpServletResponse response, 
                                          FilterChain filterChain) throws ServletException, IOException {
                
                String requestId = UUID.randomUUID().toString().substring(0, 8);
                String method = request.getMethod();
                String uri = request.getRequestURI();
                String userAgent = request.getHeader("User-Agent");
                String clientIp = getClientIpAddress(request);
                
                // Configurar MDC para logging estructurado
                MDC.put("requestId", requestId);
                MDC.put("method", method);
                MDC.put("uri", uri);
                MDC.put("clientIp", clientIp);
                MDC.put("userAgent", userAgent);
                
                long startTime = System.currentTimeMillis();
                
                try {
                    log.info("Incoming request: {} {}", method, uri);
                    
                    filterChain.doFilter(request, response);
                    
                    long duration = System.currentTimeMillis() - startTime;
                    int status = response.getStatus();
                    
                    MDC.put("status", String.valueOf(status));
                    MDC.put("duration", String.valueOf(duration));
                    
                    log.info("Request completed: {} {} - Status: {} - Duration: {}ms", 
                            method, uri, status, duration);
                    
                    // Log de auditoría para operaciones críticas
                    if (isAuditableOperation(method, uri)) {
                        AUDIT_LOGGER.info("Operation: {} {} - Status: {} - Duration: {}ms - Client: {}", 
                                method, uri, status, duration, clientIp);
                    }
                    
                } catch (Exception e) {
                    long duration = System.currentTimeMillis() - startTime;
                    MDC.put("duration", String.valueOf(duration));
                    log.error("Request failed: {} {} - Duration: {}ms", method, uri, duration, e);
                    throw e;
                } finally {
                    MDC.clear();
                }
            }
        };
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    private boolean isAuditableOperation(String method, String uri) {
        return ("POST".equals(method) && uri.contains("/api/tickets")) ||
               ("PUT".equals(method) || "DELETE".equals(method)) ||
               uri.contains("/admin/");
    }
}