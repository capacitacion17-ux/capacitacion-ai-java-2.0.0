package com.example.ticketero.exception;

/**
 * Excepción lanzada cuando no hay asesores disponibles para procesar un ticket.
 *
 * Esta excepción señala que el mensaje debe ser reencolado (NACK) para
 * reintentar cuando haya capacidad disponible.
 *
 * ESTRATEGIA FAIL-FAST:
 * - Se lanza inmediatamente (sin esperas)
 * - Worker libera recursos
 * - RabbitMQ reintenta automáticamente
 *
 * NOTA: Con max-concurrency alineado a capacidad de asesores, esta excepción
 * solo ocurre en casos excepcionales (asesor en BREAK, errores de sincronización).
 */
public class NoAdvisorAvailableException extends RuntimeException {

    public NoAdvisorAvailableException(String message) {
        super(message);
    }

    public NoAdvisorAvailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
