package com.example.ticketero.service;

import com.example.ticketero.model.entity.Advisor;
import com.example.ticketero.model.entity.Ticket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Servicio para envío de notificaciones a usuarios
 * Coordina con TelegramService para enviar mensajes
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final TelegramService telegramService;

    /**
     * Notificación cuando se crea el ticket
     * Incluye posición REAL en cola
     */
    public void notificarTicketCreado(Ticket ticket) {
        if (ticket.getTelefono() == null || ticket.getTelefono().isBlank()) {
            log.debug("Ticket {} sin teléfono, no se envía notificación", ticket.getNumero());
            return;
        }
        
        String mensaje = String.format("""
            ✅ <b>Ticket Creado</b>
            
            Tu número de turno: <b>%s</b>
            Cola: <b>%s</b>
            Posición en cola: <b>#%d</b>
            Tickets adelante: <b>%d</b>
            Espera estimada: <b>%d minutos</b>
            
            Te notificaremos cuando estés próximo.
            """,
            ticket.getNumero(),
            ticket.getQueueType().getDisplayName(),
            ticket.getPositionInQueue(),
            ticket.getPositionInQueue() - 1,
            ticket.getEstimatedWaitMinutes()
        );
        
        enviarMensaje(ticket.getTelefono(), mensaje);
        log.info("Notificación de creación enviada para ticket {}", ticket.getNumero());
    }

    /**
     * Notificación cuando el turno está próximo
     * Se envía cuando posición <= threshold (ej: 3)
     */
    public void notificarProximoTurno(Ticket ticket) {
        if (ticket.getTelefono() == null || ticket.getTelefono().isBlank()) {
            return;
        }
        
        int ticketsAdelante = ticket.getPositionInQueue() - 1;
        
        String mensaje = String.format("""
            ⏰ <b>Tu turno está próximo</b>
            
            Número: <b>%s</b>
            Posición: <b>#%d</b>
            Faltan <b>%d turno%s</b>
            
            Por favor, estate atento.
            """,
            ticket.getNumero(),
            ticket.getPositionInQueue(),
            ticketsAdelante,
            ticketsAdelante != 1 ? "s" : ""
        );
        
        enviarMensaje(ticket.getTelefono(), mensaje);
        log.info("Notificación de próximo turno enviada para ticket {}", ticket.getNumero());
    }

    /**
     * Notificación cuando es su turno
     * Incluye información del asesor y módulo
     *
     * FIX LazyInitializationException: Usa advisor pasado explícitamente
     * en lugar de acceder a ticket.getAssignedAdvisor() (lazy proxy).
     *
     * @param ticket Ticket a notificar
     * @param advisor Advisor asignado (puede ser null)
     */
    public void notificarTurnoActivo(Ticket ticket, Advisor advisor) {
        if (ticket.getTelefono() == null || ticket.getTelefono().isBlank()) {
            return;
        }

        String asesorNombre = advisor != null ? advisor.getName() : "N/A";

        String mensaje = String.format("""
            🔔 <b>¡ES TU TURNO!</b>

            Número: <b>%s</b>
            Módulo: <b>%d</b>
            Asesor: <b>%s</b>

            Por favor, acércate al módulo indicado.
            """,
            ticket.getNumero(),
            ticket.getAssignedModuleNumber(),
            asesorNombre
        );

        enviarMensaje(ticket.getTelefono(), mensaje);
        log.info("Notificación de turno activo enviada para ticket {}", ticket.getNumero());
    }

    /**
     * Overload para compatibilidad con código existente.
     * ADVERTENCIA: Puede lanzar LazyInitializationException si ticket está detached.
     */
    public void notificarTurnoActivo(Ticket ticket) {
        notificarTurnoActivo(ticket, ticket.getAssignedAdvisor());
    }

    /**
     * Notificación cuando se actualiza la posición en cola
     */
    public void notificarActualizacionPosicion(Ticket ticket) {
        if (ticket.getTelefono() == null || ticket.getTelefono().isBlank()) {
            return;
        }
        
        // Solo notificar si la posición es <= 5 (próximos)
        if (ticket.getPositionInQueue() > 5) {
            return;
        }
        
        String mensaje = String.format("""
            📊 <b>Actualización de Cola</b>
            
            Ticket: <b>%s</b>
            Nueva posición: <b>#%d</b>
            Tickets adelante: <b>%d</b>
            Espera estimada: <b>%d minutos</b>
            """,
            ticket.getNumero(),
            ticket.getPositionInQueue(),
            ticket.getPositionInQueue() - 1,
            ticket.getEstimatedWaitMinutes()
        );
        
        enviarMensaje(ticket.getTelefono(), mensaje);
        log.debug("Notificación de actualización de posición enviada para ticket {}", 
            ticket.getNumero());
    }

    /**
     * Envía mensaje a través de TelegramService
     */
    private void enviarMensaje(String telefono, String mensaje) {
        try {
            telegramService.enviarMensaje(telefono, mensaje);
            log.trace("Mensaje enviado a {}", telefono);
        } catch (Exception e) {
            log.error("Error enviando mensaje a {}: {}", telefono, e.getMessage());
            // No lanzar excepción para que el flujo principal continúe
        }
    }
}
