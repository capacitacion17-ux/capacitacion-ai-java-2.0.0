package com.example.ticketero.model.dto;

import com.example.ticketero.model.enums.QueueType;
import com.example.ticketero.model.enums.TicketStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response completo de un ticket
 */
public record TicketResponse(
    UUID codigoReferencia,
    String numero,
    QueueType queueType,
    TicketStatus status,
    Integer positionInQueue,
    Integer estimatedWaitMinutes,
    Integer ticketsAheadOfYou,
    String branchOffice,
    AdvisorInfo assignedAdvisor,
    LocalDateTime createdAt,
    LocalDateTime calledAt,
    LocalDateTime startedAt,
    LocalDateTime completedAt
) {
    /**
     * Información del asesor asignado
     */
    public record AdvisorInfo(
        Long id,
        String name,
        Integer moduleNumber
    ) {}
}
