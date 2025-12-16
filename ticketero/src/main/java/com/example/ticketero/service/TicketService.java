package com.example.ticketero.service;

import com.example.ticketero.exception.TicketNotFoundException;
import com.example.ticketero.model.dto.QueuePositionResponse;
import com.example.ticketero.model.dto.TicketCreateRequest;
import com.example.ticketero.model.dto.TicketQueueMessage;
import com.example.ticketero.model.dto.TicketResponse;
import com.example.ticketero.model.entity.Advisor;
import com.example.ticketero.model.entity.OutboxMessage;
import com.example.ticketero.model.entity.Ticket;
import com.example.ticketero.model.enums.QueueType;
import com.example.ticketero.model.enums.TicketStatus;
import com.example.ticketero.repository.OutboxMessageRepository;
import com.example.ticketero.repository.TicketRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servicio principal para gestión de tickets
 * Ahora con gestión REAL de colas, posiciones y asesores
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketService {

    private final TicketRepository ticketRepository;
    private final OutboxMessageRepository outboxMessageRepository;
    private final QueueManagementService queueManagementService;
    private final NotificationService notificationService;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;

    /**
     * Crea un nuevo ticket con gestión REAL de cola
     * Calcula posición real, tiempo real, y publica a RabbitMQ
     */
    @Transactional
    public TicketResponse crearTicket(TicketCreateRequest request) {
        log.info("Creando ticket para nationalId: {}, cola: {}", 
            request.nationalId(), request.queueType());

        // 1. Calcular posición REAL en cola
        int posicion = queueManagementService.calcularPosicionEnCola(request.queueType());
        
        // 2. Calcular tiempo estimado REAL
        int tiempoEstimado = queueManagementService.calcularTiempoEstimado(
            request.queueType(),
            posicion
        );

        // 3. Generar número de ticket
        String numeroTicket = generarNumeroTicket(request.queueType());

        // 4. Crear ticket con datos REALES
        Ticket ticket = Ticket.builder()
            .codigoReferencia(UUID.randomUUID())
            .numero(numeroTicket)
            .nationalId(request.nationalId())
            .telefono(request.telefono())
            .branchOffice(request.branchOffice())
            .queueType(request.queueType())
            .status(TicketStatus.WAITING)
            .positionInQueue(posicion)
            .estimatedWaitMinutes(tiempoEstimado)
            .build();

        // FIX PATRÓN OUTBOX: saveAndFlush garantiza que ticket está en DB
        // antes de guardar el mensaje outbox en la MISMA transacción
        ticket = ticketRepository.saveAndFlush(ticket);

        log.info("Ticket creado: {} (posición: {}, espera: {} min)",
            ticket.getNumero(), posicion, tiempoEstimado);

        // 5. Registrar métrica
        metricsService.incrementTicketsCreated(request.queueType());

        // 6. PATRÓN OUTBOX: Guardar mensaje en outbox (misma TX que ticket)
        // El OutboxPublisherService lo enviará a RabbitMQ
        guardarEnOutbox(ticket);

        // 7. Notificar posición inicial con datos REALES
        notificationService.notificarTicketCreado(ticket);

        // 8. Retornar response completo
        return toResponse(ticket);
    }

    /**
     * PATRÓN OUTBOX: Guarda mensaje en tabla outbox para envío posterior.
     *
     * Beneficios:
     * - Atomicidad: El mensaje se guarda en la misma TX que el ticket
     * - Durabilidad: Si RabbitMQ falla, el mensaje se reintenta automáticamente
     * - Consistencia: El ticket SIEMPRE existe en DB antes del mensaje
     *
     * @param ticket Ticket creado
     */
    private void guardarEnOutbox(Ticket ticket) {
        String routingKey = getQueueName(ticket.getQueueType());

        // Crear payload como JSON
        TicketQueueMessage messageContent = new TicketQueueMessage(
            ticket.getId(),
            ticket.getNumero(),
            ticket.getQueueType(),
            ticket.getTelefono()
        );

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(messageContent);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializando mensaje outbox", e);
        }

        // Guardar en outbox (misma TX que ticket)
        OutboxMessage outbox = OutboxMessage.builder()
            .aggregateType("TICKET")
            .aggregateId(ticket.getId())
            .eventType("TICKET_CREATED")
            .payload(payloadJson)
            .routingKey(routingKey)
            .status("PENDING")
            .build();

        outboxMessageRepository.save(outbox);

        log.info("Ticket {} guardado en outbox para cola: {}",
            ticket.getNumero(), routingKey);
    }

    /**
     * Obtiene el nombre de la cola RabbitMQ según el tipo
     */
    private String getQueueName(QueueType queueType) {
        return queueType.name().toLowerCase() + "-queue";
    }

    /**
     * Genera número de ticket basado en el tipo de cola
     */
    private String generarNumeroTicket(QueueType queueType) {
        // Prefijo según tipo de cola
        char prefijo = queueType.name().charAt(0);
        int numero = (int) (Math.random() * 999) + 1;
        return String.format("%c%03d", prefijo, numero);
    }

    /**
     * Obtiene ticket por código de referencia
     */
    @Transactional(readOnly = true)
    public TicketResponse obtenerTicketPorCodigo(UUID codigoReferencia) {
        log.info("Buscando ticket con código: {}", codigoReferencia);
        
        Ticket ticket = ticketRepository.findByCodigoReferencia(codigoReferencia)
            .orElseThrow(() -> new TicketNotFoundException(codigoReferencia));
        
        return toResponse(ticket);
    }

    /**
     * Obtiene posición actual en cola de un ticket
     */
    @Transactional(readOnly = true)
    public QueuePositionResponse obtenerPosicionEnCola(String numero) {
        log.info("Consultando posición de ticket: {}", numero);
        
        Ticket ticket = ticketRepository.findByNumero(numero)
            .orElseThrow(() -> new RuntimeException("Ticket not found: " + numero));
        
        // Obtener tickets adelante
        List<Ticket> ticketsAdelante = ticketRepository.findByQueueAndStatus(
            ticket.getQueueType(),
            TicketStatus.WAITING
        ).stream()
        .filter(t -> t.getPositionInQueue() < ticket.getPositionInQueue())
        .collect(Collectors.toList());
        
        return new QueuePositionResponse(
            ticket.getNumero(),
            ticket.getQueueType(),
            ticket.getPositionInQueue(),
            ticketsAdelante.size(),
            ticket.getEstimatedWaitMinutes(),
            queueManagementService.obtenerConfiguracion(ticket.getQueueType())
                .getAvgServiceTimeMinutes(),
            ticketsAdelante.stream()
                .map(Ticket::getNumero)
                .collect(Collectors.toList())
        );
    }

    /**
     * Convierte Ticket a TicketResponse
     */
    private TicketResponse toResponse(Ticket ticket) {
        TicketResponse.AdvisorInfo advisorInfo = null;
        
        if (ticket.getAssignedAdvisor() != null) {
            Advisor advisor = ticket.getAssignedAdvisor();
            advisorInfo = new TicketResponse.AdvisorInfo(
                advisor.getId(),
                advisor.getName(),
                advisor.getModuleNumber()
            );
        }
        
        int ticketsAheadOfYou = 0;
        if (ticket.getPositionInQueue() != null) {
            ticketsAheadOfYou = ticket.getPositionInQueue() - 1;
        }
        
        return new TicketResponse(
            ticket.getCodigoReferencia(),
            ticket.getNumero(),
            ticket.getQueueType(),
            ticket.getStatus(),
            ticket.getPositionInQueue(),
            ticket.getEstimatedWaitMinutes(),
            ticketsAheadOfYou,
            ticket.getBranchOffice(),
            advisorInfo,
            ticket.getCreatedAt(),
            ticket.getCalledAt(),
            ticket.getStartedAt(),
            ticket.getCompletedAt()
        );
    }

    /**
     * Obtiene todos los tickets de una cola
     */
    @Transactional(readOnly = true)
    public List<Ticket> obtenerTicketsPorCola(QueueType queueType) {
        return ticketRepository.findActiveByQueue(queueType);
    }

    /**
     * Obtiene estadísticas de cola
     */
    @Transactional(readOnly = true)
    public QueueManagementService.QueueStats obtenerEstadisticasCola(QueueType queueType) {
        return queueManagementService.obtenerEstadisticas(queueType);
    }
}
