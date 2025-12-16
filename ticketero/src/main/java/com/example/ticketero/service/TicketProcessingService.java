package com.example.ticketero.service;

import com.example.ticketero.exception.NoAdvisorAvailableException;
import com.example.ticketero.model.entity.Advisor;
import com.example.ticketero.model.entity.Ticket;
import com.example.ticketero.model.entity.TicketEvent;
import com.example.ticketero.model.enums.AdvisorStatus;
import com.example.ticketero.model.enums.EventType;
import com.example.ticketero.model.enums.QueueType;
import com.example.ticketero.model.enums.TicketStatus;
import com.example.ticketero.repository.AdvisorRepository;
import com.example.ticketero.repository.QueueConfigRepository;
import com.example.ticketero.repository.TicketEventRepository;
import com.example.ticketero.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Servicio que procesa tickets en una ÚNICA transacción.
 *
 * Este servicio resuelve el problema de fragmentación transaccional donde
 * antes había múltiples TX independientes (TX2 → TX3 → TX4 → TX5) que
 * podían dejar el sistema en estado inconsistente si fallaba alguna.
 *
 * Flujo en UNA sola TX:
 * 1. Verificar idempotencia (ticket en WAITING)
 * 2. Obtener advisor disponible (SELECT FOR UPDATE)
 * 3. Asignar advisor a ticket (status → CALLED)
 * 4. Actualizar posiciones en cola
 * 5. Iniciar atención (status → IN_PROGRESS)
 * 6. Simular tiempo de atención (configurable)
 * 7. Completar atención (status → COMPLETED)
 * 8. Liberar advisor (status → AVAILABLE)
 * 9. Registrar eventos de auditoría
 *
 * Si CUALQUIER paso falla, TODA la TX hace rollback.
 * El mensaje de RabbitMQ se re-encola (NACK).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketProcessingService {

    private final TicketRepository ticketRepository;
    private final AdvisorRepository advisorRepository;
    private final TicketEventRepository ticketEventRepository;
    private final QueueConfigRepository queueConfigRepository;
    private final NotificationService notificationService;

    /**
     * Procesa un ticket completo en una única transacción.
     *
     * @param ticketId ID del ticket a procesar
     * @param queueType Tipo de cola
     * @return true si procesó exitosamente, false si ya estaba procesado (idempotencia)
     * @throws NoAdvisorAvailableException si no hay advisors disponibles
     * @throws InterruptedException si el thread es interrumpido durante simulación
     */
    @Transactional
    public boolean procesarTicketCompleto(Long ticketId, QueueType queueType)
            throws InterruptedException {

        log.info("[PROCESS] Iniciando procesamiento de ticket ID: {} en cola: {}",
            ticketId, queueType);

        // =====================================================================
        // PASO 1: Verificar idempotencia
        // =====================================================================
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new RuntimeException("Ticket not found: " + ticketId));

        // FIX BUG: Usar .equals() en lugar de == para comparar enums
        if (!TicketStatus.WAITING.equals(ticket.getStatus())) {
            log.info("[PROCESS] Ticket {} ya procesado (status: {}). Idempotencia OK.",
                ticket.getNumero(), ticket.getStatus());
            return false;
        }

        // =====================================================================
        // PASO 2: Obtener advisor disponible con bloqueo pesimista
        // =====================================================================
        List<Advisor> disponibles = advisorRepository
            .findAvailableForQueueWithLock(queueType.name());

        if (disponibles.isEmpty()) {
            log.warn("[PROCESS] No hay advisors disponibles para cola: {}", queueType);
            throw new NoAdvisorAvailableException(
                "No hay asesores disponibles para " + queueType);
        }

        Advisor advisor = disponibles.get(0);
        log.info("[PROCESS] Advisor {} seleccionado para ticket {}",
            advisor.getName(), ticket.getNumero());

        LocalDateTime ahora = LocalDateTime.now();

        // =====================================================================
        // PASO 3: Asignar advisor a ticket (CALLED)
        // =====================================================================
        advisor.setStatus(AdvisorStatus.BUSY);
        advisor.setLastActiveAt(ahora);
        advisor.setLastHeartbeat(ahora);

        ticket.setAssignedAdvisor(advisor);
        ticket.setAssignedModuleNumber(advisor.getModuleNumber());
        ticket.setStatus(TicketStatus.CALLED);
        ticket.setCalledAt(ahora);

        registrarEvento(ticket, EventType.CALLED, advisor,
            String.format("Asignado a módulo %d", advisor.getModuleNumber()));

        log.info("[PROCESS] Ticket {} asignado a {} (Módulo {})",
            ticket.getNumero(), advisor.getName(), advisor.getModuleNumber());

        // =====================================================================
        // PASO 4: Actualizar posiciones de otros tickets en cola
        // =====================================================================
        actualizarPosicionesEnCola(queueType);

        // =====================================================================
        // PASO 5: Notificar turno activo (fuera de TX crítica, puede fallar)
        // =====================================================================
        try {
            notificationService.notificarTurnoActivo(ticket, advisor);
        } catch (Exception e) {
            log.warn("[PROCESS] Error notificando turno activo (no crítico): {}",
                e.getMessage());
        }

        // =====================================================================
        // PASO 6: Iniciar atención (IN_PROGRESS)
        // =====================================================================
        ticket.setStatus(TicketStatus.IN_PROGRESS);
        ticket.setStartedAt(LocalDateTime.now());

        registrarEvento(ticket, EventType.STARTED, advisor, "Atención iniciada");

        log.info("[PROCESS] Ticket {} en atención por {}",
            ticket.getNumero(), advisor.getName());

        // =====================================================================
        // PASO 7: Simular tiempo de atención
        // NOTA: En producción real, esto NO debería existir.
        // El advisor completa manualmente desde su dashboard.
        // Para testing, simulamos con sleep configurable.
        // =====================================================================
        int serviceTimeSeconds = obtenerTiempoServicioSegundos(queueType);
        simularAtencion(serviceTimeSeconds);

        // =====================================================================
        // PASO 8: Completar atención (COMPLETED)
        // =====================================================================
        ticket.setStatus(TicketStatus.COMPLETED);
        ticket.setCompletedAt(LocalDateTime.now());

        registrarEvento(ticket, EventType.COMPLETED, advisor, "Atención completada");

        // =====================================================================
        // PASO 9: Liberar advisor (AVAILABLE)
        // =====================================================================
        advisor.setStatus(AdvisorStatus.AVAILABLE);
        advisor.setTotalTicketsServed(advisor.getTotalTicketsServed() + 1);
        advisor.setLastActiveAt(LocalDateTime.now());
        actualizarTiempoPromedioAsesor(advisor, ticket);

        // =====================================================================
        // PASO 10: Guardar cambios (flush al commit)
        // =====================================================================
        ticketRepository.save(ticket);
        advisorRepository.save(advisor);

        log.info("[PROCESS] Ticket {} COMPLETADO. Advisor {} liberado. Total servidos: {}",
            ticket.getNumero(), advisor.getName(), advisor.getTotalTicketsServed());

        return true;
    }

    /**
     * Registra un evento de auditoría.
     */
    private void registrarEvento(Ticket ticket, EventType eventType,
                                  Advisor advisor, String notes) {
        TicketEvent event = TicketEvent.builder()
            .ticket(ticket)
            .eventType(eventType)
            .newStatus(ticket.getStatus().name())
            .advisor(advisor)
            .notes(notes)
            .build();
        ticketEventRepository.save(event);
    }

    /**
     * Umbral de posición para notificar "Próximo Turno".
     * Cuando posición <= este valor, se envía notificación.
     */
    private static final int THRESHOLD_PROXIMO_TURNO = 3;

    /**
     * Actualiza las posiciones de tickets en espera.
     * Envía notificación "Próximo Turno" cuando posición <= 3.
     */
    private void actualizarPosicionesEnCola(QueueType queueType) {
        List<Ticket> ticketsEnEspera = ticketRepository
            .findByQueueAndStatus(queueType, TicketStatus.WAITING);

        int posicion = 1;
        for (Ticket t : ticketsEnEspera) {
            if (!t.getPositionInQueue().equals(posicion)) {
                t.setPositionInQueue(posicion);
                ticketRepository.save(t);
            }

            // Notificar "Próximo Turno" si está cerca y no se ha notificado
            if (posicion <= THRESHOLD_PROXIMO_TURNO
                    && Boolean.FALSE.equals(t.getProximoTurnoNotificado())) {
                try {
                    notificationService.notificarProximoTurno(t);
                    t.setProximoTurnoNotificado(true);
                    ticketRepository.save(t);
                    log.info("[PROCESS] Notificación 'Próximo Turno' enviada a ticket {}",
                        t.getNumero());
                } catch (Exception e) {
                    log.warn("[PROCESS] Error enviando notificación próximo turno: {}",
                        e.getMessage());
                }
            }

            posicion++;
        }

        log.debug("[PROCESS] Posiciones actualizadas en cola {}: {} tickets",
            queueType, ticketsEnEspera.size());
    }

    /**
     * Obtiene tiempo de servicio en segundos según la cola.
     * Para testing: minutos → segundos (acelerado 60x).
     */
    private int obtenerTiempoServicioSegundos(QueueType queueType) {
        return queueConfigRepository.findByQueueType(queueType)
            .map(config -> config.getAvgServiceTimeMinutes())
            .orElse(5); // 5 segundos por defecto en testing
    }

    /**
     * Simula el tiempo de atención.
     * En testing: X "minutos" = X segundos (acelerado).
     * En producción: Este método NO debería existir.
     *
     * @throws InterruptedException si el thread es interrumpido
     */
    private void simularAtencion(int segundos) throws InterruptedException {
        log.debug("[PROCESS] Simulando atención de {} segundos", segundos);
        Thread.sleep(segundos * 1000L);
    }

    /**
     * Actualiza el tiempo promedio de atención del asesor.
     * Usa promedio móvil simple.
     */
    private void actualizarTiempoPromedioAsesor(Advisor advisor, Ticket ticket) {
        if (ticket.getStartedAt() != null && ticket.getCompletedAt() != null) {
            long segundos = java.time.temporal.ChronoUnit.SECONDS.between(
                ticket.getStartedAt(),
                ticket.getCompletedAt()
            );

            double minutos = segundos / 60.0;
            int totalServed = advisor.getTotalTicketsServed();
            int avgActual = advisor.getAvgServiceTimeMinutes();

            int nuevoPromedio = (int) Math.round(
                (avgActual * (totalServed - 1) + minutos) / totalServed
            );

            advisor.setAvgServiceTimeMinutes(nuevoPromedio);

            log.debug("[PROCESS] Tiempo atención: {}s. Promedio advisor {}: {} min",
                segundos, advisor.getName(), nuevoPromedio);
        }
    }
}
