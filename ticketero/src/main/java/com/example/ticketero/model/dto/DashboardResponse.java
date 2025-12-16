package com.example.ticketero.model.dto;

import com.example.ticketero.model.entity.Ticket;
import com.example.ticketero.model.enums.QueueType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response del dashboard administrativo
 */
public record DashboardResponse(
    Map<QueueType, List<Ticket>> ticketsPorCola,
    Map<String, Object> estadisticasAsesores,
    LocalDateTime timestamp
) {}
