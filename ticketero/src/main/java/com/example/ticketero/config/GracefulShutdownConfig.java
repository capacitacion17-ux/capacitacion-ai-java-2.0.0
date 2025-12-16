package com.example.ticketero.config;

import com.example.ticketero.model.entity.Advisor;
import com.example.ticketero.model.enums.AdvisorStatus;
import com.example.ticketero.repository.AdvisorRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Configuración para graceful shutdown del sistema.
 * 
 * Al recibir señal de shutdown (Ctrl+C, SIGTERM, restart):
 * 1. Detiene aceptación de nuevos trabajos (para listeners RabbitMQ)
 * 2. Espera a que terminen trabajos actuales (max 30 segundos)
 * 3. Libera asesores para evitar estado inconsistente
 * 4. Registra evento de shutdown para auditoría
 * 
 * Esto minimiza inconsistencias durante reinicios/deployments.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GracefulShutdownConfig {

    private final RabbitListenerEndpointRegistry rabbitListenerEndpointRegistry;
    private final AdvisorRepository advisorRepository;

    /**
     * Ejecutado automáticamente antes de destruir el bean.
     * Spring Boot llama este método durante el shutdown.
     */
    @PreDestroy
    public void onShutdown() {
        log.warn("═══════════════════════════════════════════════════════");
        log.warn("  GRACEFUL SHUTDOWN INICIADO                           ");
        log.warn("═══════════════════════════════════════════════════════");
        
        long startTime = System.currentTimeMillis();
        
        try {
            // PASO 1: Detener aceptación de nuevos trabajos
            detenerListeners();
            
            // PASO 2: Esperar trabajos actuales (max 30 segundos)
            esperarTrabajosActuales();
            
            // PASO 3: Liberar asesores para evitar inconsistencias
            liberarAsesores();
            
            long duration = System.currentTimeMillis() - startTime;
            log.warn("═══════════════════════════════════════════════════════");
            log.warn("  GRACEFUL SHUTDOWN COMPLETADO ({}ms)                  ", duration);
            log.warn("═══════════════════════════════════════════════════════");
            
        } catch (Exception e) {
            log.error("Error durante graceful shutdown", e);
        }
    }

    /**
     * Detiene todos los listeners de RabbitMQ.
     * No se aceptarán más mensajes de las colas.
     */
    private void detenerListeners() {
        log.info("Paso 1/3: Deteniendo listeners de RabbitMQ...");
        
        try {
            rabbitListenerEndpointRegistry.stop();
            log.info("✓ Listeners detenidos. No se aceptarán nuevos trabajos.");
        } catch (Exception e) {
            log.error("Error deteniendo listeners", e);
        }
    }

    /**
     * Espera a que terminen trabajos actuales.
     * Timeout: 30 segundos.
     * 
     * Los workers que estén en simularAtencion() tienen tiempo
     * para completar el procesamiento actual.
     */
    private void esperarTrabajosActuales() {
        log.info("Paso 2/3: Esperando trabajos actuales (max 30 segundos)...");
        
        int maxWaitSeconds = 30;
        int waitedSeconds = 0;
        
        try {
            while (waitedSeconds < maxWaitSeconds) {
                List<Advisor> busyAdvisors = advisorRepository.findByStatus(AdvisorStatus.BUSY);
                
                if (busyAdvisors.isEmpty()) {
                    log.info("✓ Todos los trabajos completados.");
                    return;
                }
                
                log.info("  Esperando... {} asesores aún ocupados", busyAdvisors.size());
                Thread.sleep(2000); // Esperar 2 segundos
                waitedSeconds += 2;
            }
            
            // Timeout alcanzado
            List<Advisor> remainingBusy = advisorRepository.findByStatus(AdvisorStatus.BUSY);
            log.warn("⚠ Timeout alcanzado. {} asesores aún ocupados. Procediendo con limpieza...", 
                remainingBusy.size());
                
        } catch (InterruptedException e) {
            log.warn("Espera interrumpida");
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Libera todos los asesores BUSY.
     * Esto evita que queden en estado inconsistente tras el reinicio.
     *
     * Los tickets que estaban en progreso serán detectados y
     * re-encolados por el RecoveryService al reiniciar.
     *
     * NOTA: Ya no usa currentTicket (relación circular eliminada).
     */
    private void liberarAsesores() {
        log.info("Paso 3/3: Liberando asesores para evitar inconsistencias...");

        try {
            List<Advisor> busyAdvisors = advisorRepository.findByStatus(AdvisorStatus.BUSY);

            for (Advisor advisor : busyAdvisors) {
                log.info("  Liberando asesor: {} (ID: {})", advisor.getName(), advisor.getId());
                advisor.setStatus(AdvisorStatus.AVAILABLE);
                advisorRepository.save(advisor);
            }

            log.info("Asesores liberados: {}", busyAdvisors.size());

        } catch (Exception e) {
            log.error("Error liberando asesores", e);
        }
    }
}
