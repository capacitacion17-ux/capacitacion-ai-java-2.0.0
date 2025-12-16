package com.example.ticketero.model.enums;

/**
 * Estados del asesor
 */
public enum AdvisorStatus {
    /**
     * Disponible para atender
     */
    AVAILABLE,
    
    /**
     * Atendiendo un cliente
     */
    BUSY,
    
    /**
     * En descanso
     */
    BREAK,
    
    /**
     * Fuera de servicio
     */
    OFFLINE
}
