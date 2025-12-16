package com.example.ticketero.model.enums;

/**
 * Estados del ticket en el flujo de atención
 */
public enum TicketStatus {
    /**
     * En cola esperando
     */
    WAITING,
    
    /**
     * Llamado (notificado que es su turno)
     */
    CALLED,
    
    /**
     * Siendo atendido
     */
    IN_PROGRESS,
    
    /**
     * Atención completada
     */
    COMPLETED,
    
    /**
     * Cancelado por usuario o timeout
     */
    CANCELLED,
    
    /**
     * No se presentó cuando fue llamado
     */
    NO_SHOW
}
