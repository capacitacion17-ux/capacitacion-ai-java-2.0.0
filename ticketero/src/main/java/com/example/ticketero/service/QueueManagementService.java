package com.example.ticketero.service;

import com.example.ticketero.model.entity.QueueConfig;
import com.example.ticketero.model.entity.Ticket;
import com.example.ticketero.model.entity.TicketEvent;
import com.example.ticketero.model.enums.EventType;
import com.example.ticketero.model.enums.QueueType;
import com.example.ticketero.model.enums.TicketStatus;
import com.example.ticketero.repository.QueueConfigRepository;
import com.example.ticketero.repository.TicketEventRepository;
import com.example.ticketero.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.example.ticketero.util.LogSanitizer.sanitize;

/**
 * Service for ticket queue management.
 * Calculates positions, estimated wait times, and updates queue states.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QueueManagementService {

    private final TicketRepository ticketRepository;
    private final QueueConfigRepository queueConfigRepository;
    private final TicketEventRepository ticketEventRepository;

    /**
     * Calcula la posición real de un nuevo ticket en la cola
     */
    @Transactional(readOnly = true)
    public int calcularPosicionEnCola(QueueType queueType) {
        log.debug("Calculando posición en cola: {}", queueType);
        
        // Contar tickets WAITING en esta cola
        int waiting = ticketRepository.countWaitingBefore(
            queueType,
            LocalDateTime.now().plusYears(100) // Todos los tickets
        );
        
        int nuevaPosicion = waiting + 1;
        log.debug("Posición calculada para {}: {}", queueType, nuevaPosicion);
        
        return nuevaPosicion;
    }

    /**
     * Calcula el tiempo estimado de espera basado en posición y config de cola
     */
    @Transactional(readOnly = true)
    public int calcularTiempoEstimado(QueueType queueType, int position) {
        QueueConfig config = queueConfigRepository.findByQueueType(queueType)
            .orElseThrow(() -> new RuntimeException("Queue config not found: " + queueType));
        
        int avgTime = config.getAvgServiceTimeMinutes();
        
        // Tiempo = (posición - 1) × tiempo promedio
        // La posición 1 tiene tiempo 0 (es el siguiente)
        int tiempoEstimado = (position - 1) * avgTime;
        
        log.debug("Tiempo estimado para posición {} en {}: {} min", 
            position, queueType, tiempoEstimado);
        
        return tiempoEstimado;
    }

    /**
     * Actualiza las posiciones de todos los tickets activos en una cola
     * Se llama cuando un ticket avanza o se completa
     */
    @Transactional
    public void actualizarPosicionesEnCola(QueueType queueType) {
        log.info("Actualizando posiciones en cola: {}", queueType);
        
        List<Ticket> ticketsActivos = ticketRepository.findActiveByQueue(queueType);
        
        if (ticketsActivos.isEmpty()) {
            log.debug("No hay tickets activos en cola {}", queueType);
            return;
        }
        
        int position = 1;
        for (Ticket ticket : ticketsActivos) {
            Integer oldPosition = ticket.getPositionInQueue();
            
            if (oldPosition == null || !oldPosition.equals(position)) {
                // Actualizar posición
                ticket.setPositionInQueue(position);
                
                // Recalcular tiempo estimado
                int estimatedTime = calcularTiempoEstimado(queueType, position);
                ticket.setEstimatedWaitMinutes(estimatedTime);
                
                ticketRepository.save(ticket);
                
                // Registrar evento
                registrarEvento(ticket, EventType.POSITION_UPDATED, oldPosition, position);
                
                log.debug("Ticket {} actualizado: posición {} → {}", 
                    ticket.getNumero(), oldPosition, position);
            }
            
            position++;
        }
        
        log.info("Posiciones actualizadas en {}. Total tickets activos: {}", 
            queueType, ticketsActivos.size());
    }

    /**
     * Obtiene el siguiente ticket de una cola (el primero en WAITING)
     */
    @Transactional
    public Optional<Ticket> obtenerSiguienteTicket(QueueType queueType) {
        log.debug("Buscando siguiente ticket en cola: {}", queueType);
        
        List<Ticket> waiting = ticketRepository.findByQueueAndStatus(
            queueType,
            TicketStatus.WAITING
        );
        
        if (waiting.isEmpty()) {
            log.debug("No hay tickets esperando en cola {}", queueType);
            return Optional.empty();
        }
        
        // El primero es el siguiente
        Ticket siguiente = waiting.get(0);
        
        log.info("Siguiente ticket en cola {}: {}", queueType, siguiente.getNumero());
        
        return Optional.of(siguiente);
    }

    /**
     * Obtiene tickets que están próximos (posición <= threshold)
     * Para enviar notificación "Faltan X turnos"
     */
    @Transactional(readOnly = true)
    public List<Ticket> obtenerTicketsProximos(QueueType queueType) {
        QueueConfig config = queueConfigRepository.findByQueueType(queueType)
            .orElseThrow(() -> new RuntimeException("Queue config not found: " + queueType));
        
        int threshold = config.getNotificationThreshold();
        
        List<Ticket> activos = ticketRepository.findActiveByQueue(queueType);
        
        List<Ticket> proximos = activos.stream()
            .filter(t -> t.getPositionInQueue() != null 
                && t.getPositionInQueue() <= threshold
                && t.getStatus() == TicketStatus.WAITING)
            .collect(Collectors.toList());
        
        log.debug("Tickets próximos en {} (threshold={}): {}", 
            queueType, threshold, proximos.size());
        
        return proximos;
    }

    /**
     * Registra un evento en el historial del ticket
     */
    public void registrarEvento(Ticket ticket, EventType eventType, 
                                Integer oldPosition, Integer newPosition) {
        TicketEvent event = TicketEvent.builder()
            .ticket(ticket)
            .eventType(eventType)
            .oldPosition(oldPosition)
            .newPosition(newPosition)
            .notes(String.format("Posición actualizada de %d a %d", oldPosition, newPosition))
            .build();
        
        ticketEventRepository.save(event);
        
        log.trace("Evento registrado: {} para ticket {}", eventType, sanitize(ticket.getNumero()));
    }

    /**
     * Obtiene configuración de una cola
     */
    @Transactional(readOnly = true)
    public QueueConfig obtenerConfiguracion(QueueType queueType) {
        return queueConfigRepository.findByQueueType(queueType)
            .orElseThrow(() -> new RuntimeException("Queue config not found: " + queueType));
    }

    /**
     * Obtiene estadísticas de una cola
     */
    @Transactional(readOnly = true)
    public QueueStats obtenerEstadisticas(QueueType queueType) {
        long waiting = ticketRepository.countByQueueTypeAndStatus(queueType, TicketStatus.WAITING);
        long called = ticketRepository.countByQueueTypeAndStatus(queueType, TicketStatus.CALLED);
        long inProgress = ticketRepository.countByQueueTypeAndStatus(queueType, TicketStatus.IN_PROGRESS);
        long completed = ticketRepository.countByQueueTypeAndStatus(queueType, TicketStatus.COMPLETED);
        
        QueueConfig config = obtenerConfiguracion(queueType);
        
        return new QueueStats(
            queueType,
            (int) waiting,
            (int) called,
            (int) inProgress,
            (int) completed,
            config.getAvgServiceTimeMinutes(),
            waiting > 0 ? (int) waiting * config.getAvgServiceTimeMinutes() : 0
        );
    }

    /**
     * Record for queue statistics.
     */
    public record QueueStats(
        QueueType queueType,
        int waiting,
        int called,
        int inProgress,
        int completed,
        int avgServiceTimeMinutes,
        int estimatedWaitTime
    ) {}
}
