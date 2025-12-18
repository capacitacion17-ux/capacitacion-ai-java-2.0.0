package com.example.ticketero.model.enums;

/**
 * Tipos de eventos en el ciclo de vida del ticket
 */
public enum EventType {
    /**
     * Ticket creado
     */
    CREATED,
    
    /**
     * Posición en cola actualizada
     */
    POSITION_UPDATED,
    
    /**
     * Usuario llamado (notificado que es su turno)
     */
    CALLED,
    
    /**
     * Atención iniciada
     */
    STARTED,
    
    /**
     * Atención completada
     */
    COMPLETED,
    
    /**
     * Ticket cancelado
     */
    CANCELLED,
    
    /**
     * Usuario no se presentó
     */
    NO_SHOW
}
