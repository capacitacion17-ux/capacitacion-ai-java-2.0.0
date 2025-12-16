package com.example.ticketero.model.dto;

import com.example.ticketero.model.entity.Ticket;
import com.example.ticketero.model.enums.QueueType;

import java.util.List;

/**
 * Response con estado de una cola específica
 */
public record QueueStatusResponse(
    QueueType queueType,
    int totalActivos,
    List<Ticket> tickets
) {}
