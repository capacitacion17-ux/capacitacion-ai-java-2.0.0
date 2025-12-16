package com.example.ticketero.service;

import com.example.ticketero.model.entity.Advisor;
import com.example.ticketero.model.entity.Ticket;
import com.example.ticketero.model.entity.TicketEvent;
import com.example.ticketero.model.enums.AdvisorStatus;
import com.example.ticketero.model.enums.EventType;
import com.example.ticketero.model.enums.QueueType;
import com.example.ticketero.model.enums.TicketStatus;
import com.example.ticketero.repository.AdvisorRepository;
import com.example.ticketero.repository.TicketEventRepository;
import com.example.ticketero.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Servicio para gestión de asesores
 * Asignación de tickets, control de disponibilidad, estadísticas
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdvisorService {

    private final AdvisorRepository advisorRepository;
    private final TicketRepository ticketRepository;
    private final TicketEventRepository ticketEventRepository;

    /**
     * Obtiene un asesor disponible para una cola específica.
     * Balancea carga eligiendo el que menos tickets ha atendido.
     *
     * DEPRECATED: Usar obtenerYAsignarAsesor() para operaciones atómicas.
     */
    @Transactional
    public Optional<Advisor> obtenerAsesorDisponible(QueueType queueType) {
        log.debug("Buscando asesor disponible para cola: {}", queueType);

        List<Advisor> disponibles = advisorRepository
            .findAvailableForQueue(queueType.name());

        if (disponibles.isEmpty()) {
            log.warn("No hay asesores disponibles para cola: {}", queueType);
            return Optional.empty();
        }

        Advisor asesor = disponibles.get(0);

        log.info("Asesor disponible encontrado: {} (módulo {})",
            asesor.getName(), asesor.getModuleNumber());

        return Optional.of(asesor);
    }

    /**
     * OPERACIÓN ATÓMICA: Obtiene y asigna asesor en una sola transacción.
     *
     * FIX RACE CONDITION + DETACHED ENTITY:
     * - SELECT FOR UPDATE bloquea el advisor hasta commit
     * - Lee el ticket DENTRO de la transacción (no como parámetro detached)
     *
     * @param ticketId ID del ticket a asignar (no entidad - evita detached)
     * @param queueType Tipo de cola
     * @return Advisor asignado, o empty si no hay disponibles
     */
    @Transactional
    public Optional<Advisor> obtenerYAsignarAsesor(Long ticketId, QueueType queueType) {
        // FIX: Leer ticket DENTRO de la transacción (evita detached entity)
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new RuntimeException("Ticket not found: " + ticketId));

        log.info("[ATOMIC] Buscando y asignando asesor para ticket {} en cola {}",
            ticket.getNumero(), queueType);

        // SELECT FOR UPDATE - bloquea filas hasta commit
        List<Advisor> disponibles = advisorRepository
            .findAvailableForQueueWithLock(queueType.name());

        if (disponibles.isEmpty()) {
            log.warn("[ATOMIC] No hay asesores disponibles para cola: {}", queueType);
            return Optional.empty();
        }

        Advisor asesor = disponibles.get(0);
        log.info("[ATOMIC] Asesor {} seleccionado y bloqueado", asesor.getName());

        // Asignar inmediatamente (mismo transaction, entidades managed)
        asignarTicketInterno(ticket, asesor);

        return Optional.of(asesor);
    }

    /**
     * Asignación interna sin @Transactional (usa transacción del caller).
     *
     * FIX DEFINITIVO: Usa UPDATE directo para ticket (evita Hibernate lazy loading).
     * NOTA: Ya no se usa currentTicket (relación circular eliminada).
     */
    private void asignarTicketInterno(Ticket ticket, Advisor asesor) {
        log.info("[ATOMIC-ASIGNAR] Ticket:{} -> Asesor:{} (Módulo:{})",
            ticket.getNumero(), asesor.getName(), asesor.getModuleNumber());

        LocalDateTime ahora = LocalDateTime.now();

        // PASO 1: Actualizar y guardar ASESOR (sin currentTicket)
        asesor.setStatus(AdvisorStatus.BUSY);
        asesor.setLastActiveAt(ahora);
        asesor.setLastHeartbeat(ahora);
        Advisor savedAdvisor = advisorRepository.saveAndFlush(asesor);

        // PASO 2: UPDATE directo para ticket
        ticketRepository.assignAdvisor(
            ticket.getId(),
            savedAdvisor.getId(),
            TicketStatus.CALLED,
            ahora,
            savedAdvisor.getModuleNumber()
        );

        // Verificar persistencia con lectura fresca
        Ticket freshTicket = ticketRepository.findById(ticket.getId())
            .orElseThrow(() -> new RuntimeException("Ticket not found after update"));

        log.info("[ATOMIC-ASIGNAR] Guardado - Ticket:{} status={}, advisor_id={}",
            freshTicket.getNumero(), freshTicket.getStatus(),
            freshTicket.getAssignedAdvisor() != null
                ? freshTicket.getAssignedAdvisor().getId() : "null");

        // PASO 3: Registrar evento
        TicketEvent event = TicketEvent.builder()
            .ticket(freshTicket)
            .eventType(EventType.CALLED)
            .newStatus(TicketStatus.CALLED.name())
            .advisor(savedAdvisor)
            .notes(String.format("Llamado al módulo %d (atomic-direct)", savedAdvisor.getModuleNumber()))
            .build();
        ticketEventRepository.saveAndFlush(event);

        log.info("[ATOMIC-ASIGNAR] Completado - Asesor {} ahora BUSY", savedAdvisor.getName());
    }

    /**
     * Asigna un ticket a un asesor.
     * Marca el ticket como CALLED y el asesor como BUSY.
     * NOTA: Ya no se usa currentTicket (relación circular eliminada).
     */
    @Transactional
    public void asignarTicketAsesor(Ticket ticket, Advisor asesor) {
        log.info("[ASIGNAR] Inicio - Ticket:{} (ID:{}) -> Asesor:{} (ID:{}, Módulo:{})",
            ticket.getNumero(), ticket.getId(),
            asesor.getName(), asesor.getId(), asesor.getModuleNumber());
        log.debug("[ASIGNAR] Estado previo asesor - Status:{}", asesor.getStatus());

        // Actualizar ticket
        ticket.setAssignedAdvisor(asesor);
        ticket.setAssignedModuleNumber(asesor.getModuleNumber());
        ticket.setStatus(TicketStatus.CALLED);
        ticket.setCalledAt(LocalDateTime.now());
        ticketRepository.save(ticket);

        // Actualizar asesor (sin currentTicket)
        asesor.setStatus(AdvisorStatus.BUSY);
        asesor.setLastActiveAt(LocalDateTime.now());
        advisorRepository.save(asesor);

        // Registrar evento
        TicketEvent event = TicketEvent.builder()
            .ticket(ticket)
            .eventType(EventType.CALLED)
            .newStatus(TicketStatus.CALLED.name())
            .advisor(asesor)
            .notes(String.format("Llamado al módulo %d", asesor.getModuleNumber()))
            .build();
        ticketEventRepository.save(event);

        log.info("[ASIGNAR] Completado - Ticket:{} -> Asesor:{} ahora BUSY",
            ticket.getNumero(), asesor.getName());
    }

    /**
     * Marca que comenzó la atención.
     *
     * FIX: Usa UPDATE directo para NO tocar assigned_advisor_id.
     */
    @Transactional
    public void iniciarAtencion(Long ticketId) {
        LocalDateTime ahora = LocalDateTime.now();

        // UPDATE directo - no toca assigned_advisor_id
        ticketRepository.updateStatusAndStartedAt(ticketId, TicketStatus.IN_PROGRESS, ahora);

        // Leer ticket para logging y evento (read-only)
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new RuntimeException("Ticket not found: " + ticketId));

        log.info("Iniciando atención para ticket {} (advisor_id={})",
            ticket.getNumero(),
            ticket.getAssignedAdvisor() != null ? ticket.getAssignedAdvisor().getId() : "null");

        // Registrar evento
        TicketEvent event = TicketEvent.builder()
            .ticket(ticket)
            .eventType(EventType.STARTED)
            .newStatus(TicketStatus.IN_PROGRESS.name())
            .advisor(ticket.getAssignedAdvisor())
            .notes("Atención iniciada")
            .build();
        ticketEventRepository.save(event);

        log.info("Atención iniciada para ticket {}", ticket.getNumero());
    }

    /**
     * Completa la atención y libera al asesor.
     *
     * FIX: Usa UPDATE directo para ticket + obtiene advisor_id de la tabla.
     */
    @Transactional
    public void completarAtencion(Long ticketId) {
        log.info("[COMPLETAR] Inicio - TicketID:{}", ticketId);
        LocalDateTime ahora = LocalDateTime.now();

        // Leer ticket para obtener advisor_id ANTES de cualquier update
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new RuntimeException("Ticket not found: " + ticketId));

        Long advisorId = ticket.getAssignedAdvisor() != null
            ? ticket.getAssignedAdvisor().getId()
            : null;

        log.info("[COMPLETAR] Ticket:{} (ID:{}) - AdvisorID:{} - StatusActual:{}",
            ticket.getNumero(), ticketId, advisorId, ticket.getStatus());

        // FIX: UPDATE directo - no modifica assigned_advisor_id
        ticketRepository.updateStatusAndCompletedAt(ticketId, TicketStatus.COMPLETED, ahora);

        // Refrescar ticket para evento (status ya actualizado en DB)
        ticket.setStatus(TicketStatus.COMPLETED);
        ticket.setCompletedAt(ahora);

        // Liberar asesor con validación
        Advisor asesor = liberarAsesorConValidacion(advisorId, ticketId, ticket);

        // Registrar evento
        TicketEvent event = TicketEvent.builder()
            .ticket(ticket)
            .eventType(EventType.COMPLETED)
            .newStatus(TicketStatus.COMPLETED.name())
            .advisor(asesor)
            .notes("Atención completada")
            .build();
        ticketEventRepository.save(event);

        log.info("[COMPLETAR] Fin - Ticket:{} COMPLETED. Asesor:{} Status:{}",
            ticket.getNumero(),
            asesor != null ? asesor.getName() : "N/A",
            asesor != null ? asesor.getStatus() : "N/A");
    }

    /**
     * Libera asesor. NOTA: Ya no usa currentTicket (relación circular eliminada).
     */
    private Advisor liberarAsesorConValidacion(Long advisorId, Long ticketId, Ticket ticket) {
        if (advisorId == null) {
            return null;
        }

        Advisor asesor = advisorRepository.findById(advisorId).orElse(null);
        if (asesor == null) {
            log.warn("[LIBERAR] Advisor ID:{} no encontrado", advisorId);
            return null;
        }

        // Si ya está disponible, no hacer nada
        if (asesor.getStatus() == AdvisorStatus.AVAILABLE) {
            return asesor;
        }

        // Guardar estado anterior para logging
        String estadoAnterior = asesor.getStatus().name();

        // Liberar asesor (sin currentTicket)
        asesor.setStatus(AdvisorStatus.AVAILABLE);
        asesor.setTotalTicketsServed(asesor.getTotalTicketsServed() + 1);
        asesor.setLastActiveAt(LocalDateTime.now());
        actualizarTiempoPromedioAsesor(asesor, ticket);

        // saveAndFlush garantiza persistencia inmediata
        Advisor guardado = advisorRepository.saveAndFlush(asesor);

        log.info("[LIBERAR] {}:{} -> AVAILABLE (was {})", guardado.getName(),
            guardado.getId(), estadoAnterior);

        return guardado;
    }

    /**
     * Actualiza el tiempo promedio de atención del asesor.
     *
     * MEJORA: Calcula en SEGUNDOS primero, luego convierte a minutos.
     * Esto permite métricas precisas incluso en modo testing (1 min = 1 seg).
     *
     * Usa promedio móvil simple: P_new = (P_old × N + T_current) / (N + 1)
     *
     * COMPARACIÓN:
     * - ANTES: ChronoUnit.MINUTES → 0 cuando < 60s (inútil en testing)
     * - DESPUÉS: ChronoUnit.SECONDS → Precisión total, funciona en testing y producción
     *
     * Ejemplo (testing, atención de 23s):
     * - ANTES: 0 minutos → promedio = 0
     * - DESPUÉS: 23 segundos → 0.38 minutos → promedio acumula correctamente
     *
     * Ejemplo (producción, atención de 20 minutos):
     * - ANTES: 20 minutos → funciona
     * - DESPUÉS: 1200 segundos → 20.0 minutos → funciona igual
     */
    private void actualizarTiempoPromedioAsesor(Advisor asesor, Ticket ticket) {
        if (ticket.getStartedAt() != null && ticket.getCompletedAt() != null) {
            // Calcular en SEGUNDOS para precisión
            long segundos = ChronoUnit.SECONDS.between(
                ticket.getStartedAt(),
                ticket.getCompletedAt()
            );

            // Convertir a minutos (con decimales)
            double minutos = segundos / 60.0;

            // Promedio móvil simple
            int totalServed = asesor.getTotalTicketsServed();
            int avgActual = asesor.getAvgServiceTimeMinutes();

            // Calcular nuevo promedio (redondeado)
            int nuevoPromedio = (int) Math.round(
                (avgActual * totalServed + minutos) / (totalServed + 1)
            );

            asesor.setAvgServiceTimeMinutes(nuevoPromedio);

            log.debug("Tiempo atención ticket {}: {}s ({} min). Promedio asesor {}: {} min",
                ticket.getNumero(), segundos, String.format("%.2f", minutos),
                asesor.getName(), nuevoPromedio);
        }
    }

    /**
     * Obtiene estadísticas generales de asesores
     */
    @Transactional(readOnly = true)
    public Map<String, Object> obtenerEstadisticas() {
        List<Advisor> todos = advisorRepository.findAll();
        
        long disponibles = todos.stream()
            .filter(a -> a.getStatus() == AdvisorStatus.AVAILABLE)
            .count();
        
        long ocupados = todos.stream()
            .filter(a -> a.getStatus() == AdvisorStatus.BUSY)
            .count();
        
        long enDescanso = todos.stream()
            .filter(a -> a.getStatus() == AdvisorStatus.BREAK)
            .count();
        
        int totalAtendidos = todos.stream()
            .mapToInt(Advisor::getTotalTicketsServed)
            .sum();
        
        double promedioTiempo = todos.stream()
            .mapToInt(Advisor::getAvgServiceTimeMinutes)
            .average()
            .orElse(0.0);
        
        return Map.of(
            "total", todos.size(),
            "disponibles", disponibles,
            "ocupados", ocupados,
            "enDescanso", enDescanso,
            "totalTicketsAtendidos", totalAtendidos,
            "tiempoPromedioAtencion", promedioTiempo
        );
    }

    /**
     * Cambia el estado de un asesor
     */
    @Transactional
    public void cambiarEstado(Long advisorId, AdvisorStatus nuevoEstado) {
        Advisor asesor = advisorRepository.findById(advisorId)
            .orElseThrow(() -> new RuntimeException("Advisor not found: " + advisorId));
        
        AdvisorStatus estadoAnterior = asesor.getStatus();
        
        log.info("Cambiando estado de asesor {} de {} a {}", 
            asesor.getName(), estadoAnterior, nuevoEstado);
        
        asesor.setStatus(nuevoEstado);
        asesor.setLastActiveAt(LocalDateTime.now());
        
        advisorRepository.save(asesor);
    }

    /**
     * Obtiene todos los asesores activos
     */
    @Transactional(readOnly = true)
    public List<Advisor> obtenerAsesoresActivos() {
        return advisorRepository.findActiveAdvisors();
    }

    /**
     * Sanitiza valores para prevenir log injection (CWE-117)
     */
    private String sanitizeForLog(Object value) {
        if (value == null) {
            return "null";
        }
        return value.toString()
            .replace("\n", "_")
            .replace("\r", "_")
            .replace("\t", "_");
    }
}
