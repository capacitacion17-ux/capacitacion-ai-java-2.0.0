package com.example.ticketero.controller;

import com.example.ticketero.model.dto.QueuePositionResponse;
import com.example.ticketero.model.dto.TicketCreateRequest;
import com.example.ticketero.model.dto.TicketResponse;
import com.example.ticketero.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Tickets", description = "Gestión de tickets y turnos")
public class TicketController {

    private final TicketService ticketService;

    /**
     * Crea un nuevo ticket
     * Ahora retorna posición REAL en cola y tiempo estimado REAL
     * 
     * POST /api/tickets
     */
    @PostMapping
    @Operation(summary = "Crear nuevo ticket", description = "Crea un nuevo ticket en la cola especificada")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Ticket creado exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
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
    @Operation(summary = "Obtener ticket", description = "Obtiene un ticket por su código de referencia")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Ticket encontrado"),
        @ApiResponse(responseCode = "404", description = "Ticket no encontrado")
    })
    public ResponseEntity<TicketResponse> obtenerTicket(
        @Parameter(description = "Código de referencia del ticket") @PathVariable UUID codigoReferencia
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
    @Operation(summary = "Obtener posición en cola", description = "Obtiene la posición actual de un ticket en la cola")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Posición obtenida exitosamente"),
        @ApiResponse(responseCode = "404", description = "Ticket no encontrado")
    })
    public ResponseEntity<QueuePositionResponse> obtenerPosicion(
        @Parameter(description = "Número del ticket") @PathVariable String numero
    ) {
        log.info("GET /api/tickets/{}/position - Consultando posición", sanitize(numero));
        
        QueuePositionResponse response = ticketService.obtenerPosicionEnCola(numero);
        
        return ResponseEntity.ok(response);
    }

}
