package com.example.ticketero.controller;

import com.example.ticketero.model.dto.QueuePositionResponse;
import com.example.ticketero.model.dto.TicketCreateRequest;
import com.example.ticketero.model.dto.TicketResponse;
import com.example.ticketero.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.example.ticketero.util.LogSanitizer.sanitize;

/**
 * Controller for ticket management.
 * Handles ticket creation and status queries with real queue positioning.
 */
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@Slf4j
public class TicketController {

    private final TicketService ticketService;

    /**
     * Crea un nuevo ticket
     * Ahora retorna posición REAL en cola y tiempo estimado REAL
     * 
     * POST /api/tickets
     */
    @PostMapping
    public ResponseEntity<TicketResponse> crearTicket(
        @Valid @RequestBody TicketCreateRequest request
    ) {
        log.info("POST /api/tickets - Creando ticket. Cola: {}, NationalId: {}", 
            request.queueType(), request.nationalId());
        
        TicketResponse response = ticketService.crearTicket(request);
        
        log.info("Ticket creado: {} (posición: #{})", 
            response.numero(), response.positionInQueue());
        
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response);
    }

    /**
     * Obtiene un ticket por su código de referencia
     * 
     * GET /api/tickets/{codigoReferencia}
     */
    @GetMapping("/{codigoReferencia}")
    public ResponseEntity<TicketResponse> obtenerTicket(
        @PathVariable UUID codigoReferencia
    ) {
        log.info("GET /api/tickets/{} - Obteniendo ticket", codigoReferencia);
        
        TicketResponse response = ticketService.obtenerTicketPorCodigo(codigoReferencia);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Obtiene la posición actual en cola de un ticket por su número
     * 
     * GET /api/tickets/{numero}/position
     */
    @GetMapping("/{numero}/position")
    public ResponseEntity<QueuePositionResponse> obtenerPosicion(
        @PathVariable String numero
    ) {
        log.info("GET /api/tickets/{}/position - Consultando posición", sanitize(numero));
        
        QueuePositionResponse response = ticketService.obtenerPosicionEnCola(numero);
        
        return ResponseEntity.ok(response);
    }

}
