package com.example.ticketero.testutil;

import com.example.ticketero.model.dto.TicketCreateRequest;
import com.example.ticketero.model.entity.*;
import com.example.ticketero.model.enums.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Builder para crear datos de prueba consistentes.
 */
public class TestDataBuilder {

    // ============================================================
    // TICKETS
    // ============================================================
    
    public static Ticket.TicketBuilder ticketWaiting() {
        return Ticket.builder()
            .id(1L)
            .codigoReferencia(UUID.randomUUID())
            .numero("C001")
            .nationalId("12345678")
            .telefono("+56912345678")
            .branchOffice("Sucursal Centro")
            .queueType(QueueType.CAJA)
            .status(TicketStatus.WAITING)
            .positionInQueue(1)
            .estimatedWaitMinutes(5)
            .createdAt(LocalDateTime.now());
    }
    
    public static Ticket.TicketBuilder ticketInProgress() {
        return ticketWaiting()
            .status(TicketStatus.IN_PROGRESS)
            .calledAt(LocalDateTime.now().minusMinutes(2))
            .startedAt(LocalDateTime.now().minusMinutes(1));
    }
    
    public static Ticket.TicketBuilder ticketCompleted() {
        return ticketInProgress()
            .status(TicketStatus.COMPLETED)
            .completedAt(LocalDateTime.now());
    }

    // ============================================================
    // ADVISORS
    // ============================================================
    
    public static Advisor.AdvisorBuilder advisorAvailable() {
        return Advisor.builder()
            .id(1L)
            .name("María López")
            .email("maria.lopez@banco.com")
            .moduleNumber(1)
            .queueTypesJson("[\"CAJA\"]")
            .status(AdvisorStatus.AVAILABLE)
            .avgServiceTimeMinutes(5)
            .totalTicketsServed(10)
            .createdAt(LocalDateTime.now())
            .lastActiveAt(LocalDateTime.now())
            .lastHeartbeat(LocalDateTime.now())
            .recoveryCount(0);
    }
    
    public static Advisor.AdvisorBuilder advisorBusy() {
        return advisorAvailable()
            .status(AdvisorStatus.BUSY);
    }

    // ============================================================
    // QUEUE CONFIG
    // ============================================================
    
    public static QueueConfig.QueueConfigBuilder queueConfigCaja() {
        return QueueConfig.builder()
            .id(1L)
            .queueType(QueueType.CAJA)
            .displayName("Caja")
            .avgServiceTimeMinutes(5)
            .notificationThreshold(3)
            .priority(1)
            .isActive(true)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now());
    }

    // ============================================================
    // REQUESTS
    // ============================================================
    
    public static TicketCreateRequest validTicketRequest() {
        return new TicketCreateRequest(
            "12345678",
            "+56912345678",
            "Sucursal Centro",
            QueueType.CAJA
        );
    }
    
    public static TicketCreateRequest ticketRequestSinTelefono() {
        return new TicketCreateRequest(
            "12345678",
            null,
            "Sucursal Centro",
            QueueType.CAJA
        );
    }

    // ============================================================
    // OUTBOX
    // ============================================================
    
    public static OutboxMessage.OutboxMessageBuilder outboxPending() {
        return OutboxMessage.builder()
            .id(1L)
            .aggregateType("TICKET")
            .aggregateId(1L)
            .eventType("TICKET_CREATED")
            .payload("{\"ticketId\":1,\"numero\":\"C001\"}")
            .routingKey("caja-queue")
            .status("PENDING")
            .retryCount(0)
            .maxRetries(5)
            .createdAt(LocalDateTime.now());
    }
}