package com.example.ticketero.model.dto;

import com.example.ticketero.model.enums.QueueType;

import java.io.Serializable;

/**
 * Mensaje que se publica en RabbitMQ para procesamiento de tickets
 */
public record TicketQueueMessage(
    Long ticketId,
    String numero,
    QueueType queueType,
    String telefono
) implements Serializable {}
