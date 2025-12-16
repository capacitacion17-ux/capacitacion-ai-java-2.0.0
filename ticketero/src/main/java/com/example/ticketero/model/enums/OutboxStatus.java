package com.example.ticketero.model.enums;

/**
 * Status values for outbox messages.
 * Used in the transactional outbox pattern for reliable messaging.
 */
public enum OutboxStatus {
    /**
     * Message is waiting to be sent to the message broker.
     */
    PENDING,

    /**
     * Message was successfully sent to the message broker.
     */
    SENT,

    /**
     * Message failed to send after maximum retry attempts.
     */
    FAILED
}
