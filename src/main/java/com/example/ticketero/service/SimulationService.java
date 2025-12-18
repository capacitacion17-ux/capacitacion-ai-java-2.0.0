package com.example.ticketero.service;

import com.example.ticketero.model.entity.Ticket;
import com.example.ticketero.model.enums.QueueType;
import com.example.ticketero.model.enums.TicketStatus;
import com.example.ticketero.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Servicio para simulación automática del flujo de tickets
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SimulationService {

    private final TicketRepository ticketRepository;
    private final AdvisorService advisorService;
    private final QueueManagementService queueManagementService;
    private final NotificationService notificationService;
    
    private final AtomicBoolean simulationRunning = new AtomicBoolean(false);

    /**
     * Inicia la simulación automática
     */
    @Async
    public CompletableFuture<String> iniciarSimulacion() {
        if (!simulationRunning.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture("Simulación ya está en ejecución");
        }

        log.info("Iniciando simulación automática");
        
        try {
            while (simulationRunning.get()) {
                procesarTicketsPendientes();
                Thread.sleep(30000); // Procesar cada 30 segundos
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Simulación interrumpida");
        } catch (Exception e) {
            log.error("Error en simulación: {}", e.getMessage(), e);
        } finally {
            simulationRunning.set(false);
        }

        return CompletableFuture.completedFuture("Simulación finalizada");
    }

    /**
     * Detiene la simulación
     */
    public String detenerSimulacion() {
        if (simulationRunning.compareAndSet(true, false)) {
            log.info("Deteniendo simulación automática");
            return "Simulación detenida";
        }
        return "No hay simulación en ejecución";
    }

    /**
     * Verifica si la simulación está activa
     */
    public boolean isSimulationRunning() {
        return simulationRunning.get();
    }

    /**
     * Procesa tickets pendientes en todas las colas
     */
    private void procesarTicketsPendientes() {
        for (QueueType queueType : QueueType.values()) {
            procesarColaSimulada(queueType);
        }
    }

    /**
     * Procesa una cola específica en modo simulación
     */
    private void procesarColaSimulada(QueueType queueType) {
        try {
            // Buscar tickets WAITING que han esperado suficiente tiempo
            List<Ticket> ticketsEsperando = ticketRepository.findByQueueAndStatus(queueType, TicketStatus.WAITING);
            
            for (Ticket ticket : ticketsEsperando) {
                if (deberiaLlamarTicket(ticket)) {
                    llamarSiguienteTicketSimulado(queueType);
                    break; // Solo procesar uno por vez por cola
                }
            }

            // Procesar tickets CALLED para iniciar atención
            List<Ticket> ticketsLlamados = ticketRepository.findByQueueAndStatus(queueType, TicketStatus.CALLED);
            for (Ticket ticket : ticketsLlamados) {
                if (deberiaIniciarAtencion(ticket)) {
                    advisorService.iniciarAtencion(ticket.getId());
                    log.info("Simulación: Iniciada atención para ticket {}", ticket.getNumero());
                    break;
                }
            }

            // Procesar tickets IN_PROGRESS para completar
            List<Ticket> ticketsEnProgreso = ticketRepository.findByQueueAndStatus(queueType, TicketStatus.IN_PROGRESS);
            for (Ticket ticket : ticketsEnProgreso) {
                if (deberiaCompletarAtencion(ticket)) {
                    advisorService.completarAtencion(ticket.getId());
                    log.info("Simulación: Completada atención para ticket {}", ticket.getNumero());
                    
                    // Actualizar posiciones en cola después de completar
                    queueManagementService.actualizarPosicionesEnCola(queueType);
                    break;
                }
            }

        } catch (Exception e) {
            log.error("Error procesando cola {} en simulación: {}", queueType, e.getMessage());
        }
    }

    /**
     * Determina si un ticket debería ser llamado (simulación de 5 minutos de espera)
     */
    private boolean deberiaLlamarTicket(Ticket ticket) {
        if (ticket.getPositionInQueue() == null || ticket.getPositionInQueue() > 1) {
            return false; // Solo llamar al primero en la cola
        }
        
        // En simulación, considerar que han pasado 5 minutos si el ticket fue creado hace más de 1 minuto
        return ticket.getCreatedAt().isBefore(java.time.LocalDateTime.now().minusMinutes(1));
    }

    /**
     * Determina si debería iniciar la atención (después de ser llamado)
     */
    private boolean deberiaIniciarAtencion(Ticket ticket) {
        // Iniciar atención 30 segundos después de ser llamado
        return ticket.getCalledAt() != null && 
               ticket.getCalledAt().isBefore(java.time.LocalDateTime.now().minusSeconds(30));
    }

    /**
     * Determina si debería completar la atención
     */
    private boolean deberiaCompletarAtencion(Ticket ticket) {
        // Completar atención 2 minutos después de iniciarla
        return ticket.getStartedAt() != null && 
               ticket.getStartedAt().isBefore(java.time.LocalDateTime.now().minusMinutes(2));
    }

    /**
     * Llama al siguiente ticket en modo simulación
     */
    private void llamarSiguienteTicketSimulado(QueueType queueType) {
        try {
            var siguienteTicket = queueManagementService.obtenerSiguienteTicket(queueType);
            
            if (siguienteTicket.isPresent()) {
                Ticket ticket = siguienteTicket.get();
                
                // Intentar asignar asesor
                var asesor = advisorService.obtenerYAsignarAsesor(ticket.getId(), queueType);
                
                if (asesor.isPresent()) {
                    log.info("Simulación: Ticket {} llamado y asignado a asesor {}", 
                        ticket.getNumero(), asesor.get().getName());
                    
                    // Enviar notificación
                    notificationService.notificarTurnoActivo(ticket, asesor.get());
                    
                    // Actualizar posiciones en cola
                    queueManagementService.actualizarPosicionesEnCola(queueType);
                } else {
                    log.debug("Simulación: No hay asesores disponibles para cola {}", queueType);
                }
            }
        } catch (Exception e) {
            log.error("Error llamando siguiente ticket en simulación para cola {}: {}", queueType, e.getMessage());
        }
    }
}