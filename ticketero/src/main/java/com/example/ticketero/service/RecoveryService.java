package com.example.ticketero.service;

import com.example.ticketero.model.entity.Advisor;
import com.example.ticketero.model.entity.RecoveryEvent;
import com.example.ticketero.model.entity.Ticket;
import com.example.ticketero.model.enums.AdvisorStatus;
import com.example.ticketero.model.enums.TicketStatus;
import com.example.ticketero.repository.AdvisorRepository;
import com.example.ticketero.repository.RecoveryEventRepository;
import com.example.ticketero.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static com.example.ticketero.util.LogSanitizer.sanitize;

/**
 * Service for automatic system recovery.
 * Detecta y corrige inconsistencias causadas por crashes o reinicios.
 * 
 * Funcionalidades:
 * - Detección de workers muertos (sin heartbeat > 60s)
 * - Liberación automática de asesores
 * - Re-encolamiento de tickets huérfanos
 * - Auditoría de recuperaciones
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecoveryService {

    private final AdvisorRepository advisorRepository;
    private final TicketRepository ticketRepository;
    private final RecoveryEventRepository recoveryEventRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange}")
    private String exchangeName;

    @Value("${app.recovery.heartbeat-timeout-seconds:60}")
    private int heartbeatTimeoutSeconds;

    /**
     * Detecta y recupera workers muertos.
     * Se ejecuta cada 30 segundos.
     * 
     * Un worker se considera muerto si:
     * - status = BUSY
     * - last_heartbeat > 60 segundos atrás (o NULL)
     */
    @Scheduled(fixedDelay = 30000) // Cada 30 segundos
    @Transactional
    public void detectarYRecuperarWorkersMuertos() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(heartbeatTimeoutSeconds);
        
        List<Advisor> deadWorkers = advisorRepository.findDeadWorkers(threshold);
        
        if (!deadWorkers.isEmpty()) {
            log.warn("Detectados {} workers muertos (sin heartbeat > {}s). Iniciando recuperación...", 
                deadWorkers.size(), heartbeatTimeoutSeconds);
            
            for (Advisor advisor : deadWorkers) {
                recuperarAsesor(advisor);
            }
            
            log.info("Recuperación completada. {} asesores liberados", deadWorkers.size());
        }
    }

    /**
     * Recupera un asesor específico del estado inconsistente.
     *
     * Pasos:
     * 1. Registra evento de recuperación
     * 2. Libera el asesor (status = AVAILABLE)
     * 3. Si tiene ticket asignado, lo reencola
     *
     * NOTA: Ya no usa currentTicket (relación circular eliminada).
     * Busca el ticket activo por query.
     */
    private void recuperarAsesor(Advisor advisor) {
        log.info("Recuperando asesor: {} (ID: {}, último heartbeat: {})",
            advisor.getName(), advisor.getId(), advisor.getLastHeartbeat());

        // 1. Buscar ticket actual por query (reemplaza currentTicket)
        Ticket currentTicket = ticketRepository.findCurrentTicketForAdvisor(advisor.getId())
            .orElse(null);

        // 2. Registrar evento de auditoría
        RecoveryEvent event = RecoveryEvent.builder()
            .advisor(advisor)
            .ticket(currentTicket)
            .recoveryType("DEAD_WORKER")
            .oldAdvisorStatus(advisor.getStatus().name())
            .oldTicketStatus(currentTicket != null ? currentTicket.getStatus().name() : null)
            .notes(String.format(
                "Worker muerto detectado. Último heartbeat: %s. Asesor liberado y ticket reencolado.",
                advisor.getLastHeartbeat() != null ? advisor.getLastHeartbeat().toString() : "NULL"
            ))
            .build();

        recoveryEventRepository.save(event);

        // 3. Re-encolar ticket si existe y no está completado
        if (currentTicket != null && currentTicket.getStatus() != TicketStatus.COMPLETED) {
            reencolarTicket(currentTicket);
        }

        // 4. Liberar asesor (sin currentTicket)
        advisor.setStatus(AdvisorStatus.AVAILABLE);
        advisor.setLastHeartbeat(LocalDateTime.now());
        advisorRepository.incrementRecoveryCount(advisor.getId());
        advisorRepository.save(advisor);

        log.info("Asesor {} recuperado exitosamente", advisor.getName());
    }

    /**
     * Reencola un ticket huérfano a RabbitMQ.
     * 
     * El ticket vuelve a estado WAITING y se envía nuevamente a la cola
     * para que otro worker lo procese.
     */
    private void reencolarTicket(Ticket ticket) {
        log.info("Re-encolando ticket huérfano: {} (status: {})", 
            ticket.getNumero(), ticket.getStatus());
        
        // Revertir estado a WAITING
        ticket.setStatus(TicketStatus.WAITING);
        ticket.setAssignedAdvisor(null);
        ticket.setAssignedModuleNumber(null);
        ticket.setCalledAt(null);
        ticket.setStartedAt(null);
        
        ticketRepository.save(ticket);
        
        // Enviar a RabbitMQ con persistencia
        String queueName = ticket.getQueueType().name().toLowerCase() + "-queue";

        try {
            rabbitTemplate.convertAndSend(
                exchangeName,
                queueName,
                new com.example.ticketero.model.dto.TicketQueueMessage(
                    ticket.getId(),
                    ticket.getNumero(),
                    ticket.getQueueType(),
                    ticket.getTelefono()
                ),
                msg -> {
                    msg.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return msg;
                }
            );

            log.info("Ticket {} re-encolado exitosamente a {} (PERSISTENT)",
                ticket.getNumero(), queueName);
                
        } catch (Exception e) {
            log.error("Error re-encolando ticket {}: {}", 
                ticket.getNumero(), e.getMessage(), e);
        }
    }

    /**
     * Recuperación manual de un asesor específico.
     * Útil para intervenciones de operaciones.
     *
     * NOTA: Ya no usa currentTicket (relación circular eliminada).
     *
     * @param advisorId id del asesor a recuperar
     */
    @Transactional
    public void recuperarAsesorManual(Long advisorId) {
        Advisor advisor = advisorRepository.findById(advisorId)
            .orElseThrow(() -> new RuntimeException("Asesor no encontrado: " + advisorId));

        log.info("Recuperación MANUAL iniciada para asesor: {}", advisor.getName());

        // Buscar ticket actual por query (reemplaza currentTicket)
        Ticket currentTicket = ticketRepository.findCurrentTicketForAdvisor(advisor.getId())
            .orElse(null);

        RecoveryEvent event = RecoveryEvent.builder()
            .advisor(advisor)
            .ticket(currentTicket)
            .recoveryType("MANUAL")
            .oldAdvisorStatus(advisor.getStatus().name())
            .oldTicketStatus(currentTicket != null ? currentTicket.getStatus().name() : null)
            .notes("Recuperación manual solicitada por operaciones")
            .build();

        recoveryEventRepository.save(event);

        if (currentTicket != null && currentTicket.getStatus() != TicketStatus.COMPLETED) {
            reencolarTicket(currentTicket);
        }

        // Liberar asesor (sin currentTicket)
        advisor.setStatus(AdvisorStatus.AVAILABLE);
        advisor.setLastHeartbeat(LocalDateTime.now());
        advisorRepository.save(advisor);

        log.info("Asesor {} recuperado manualmente", sanitize(advisor.getName()));
    }

    /**
     * Gets recovery statistics.
     *
     * @return total recovery events in the last 24 hours
     */
    @Transactional(readOnly = true)
    public long getRecoveryStats() {
        LocalDateTime last24Hours = LocalDateTime.now().minusHours(24);
        return recoveryEventRepository.findByDetectedAtAfterOrderByDetectedAtDesc(last24Hours).size();
    }
}
