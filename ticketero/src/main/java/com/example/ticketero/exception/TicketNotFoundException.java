package com.example.ticketero.exception;

import java.util.UUID;

public class TicketNotFoundException extends RuntimeException {
    
    public TicketNotFoundException(UUID codigoReferencia) {
        super("Ticket no encontrado con código: " + codigoReferencia);
    }
}
