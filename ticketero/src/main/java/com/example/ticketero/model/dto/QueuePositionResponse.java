package com.example.ticketero.model.dto;

import com.example.ticketero.model.enums.QueueType;

import java.util.List;

/**
 * Response con información de posición en cola
 */
public record QueuePositionResponse(
    String numero,
    QueueType queueType,
    Integer position,
    Integer ticketsAheadOfYou,
    Integer estimatedWaitMinutes,
    Integer averageServiceTime,
    List<String> ticketsAhead
) {}
