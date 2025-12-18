package com.example.ticketero.controller;

import com.example.ticketero.model.dto.AdvisorCreateRequest;
import com.example.ticketero.model.dto.DashboardResponse;
import com.example.ticketero.model.dto.QueueStatusResponse;
import com.example.ticketero.model.dto.TicketResponse;
import com.example.ticketero.model.entity.Advisor;
import com.example.ticketero.model.entity.Ticket;
import com.example.ticketero.model.enums.AdvisorStatus;
import com.example.ticketero.model.enums.QueueType;
import com.example.ticketero.repository.TicketRepository;
import com.example.ticketero.service.AdvisorService;
import com.example.ticketero.service.NotificationService;
import com.example.ticketero.service.QueueManagementService;
import com.example.ticketero.service.SimulationService;
import com.example.ticketero.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.example.ticketero.util.LogSanitizer.sanitize;

/**
 * Controller for administrative dashboard.
 * Provides APIs for real-time system monitoring.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final QueueManagementService queueManagementService;
    private final AdvisorService advisorService;
    private final TicketRepository ticketRepository;
    private final SimulationService simulationService;
    private final TicketService ticketService;
    private final NotificationService notificationService;

    /**
     * Dashboard principal: estado general del sistema
     * 
     * GET /api/admin/dashboard
     */
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> getDashboard() {
        log.info("GET /api/admin/dashboard - Obteniendo estado del sistema");
        
        // Obtener tickets por cola
        Map<QueueType, List<Ticket>> ticketsPorCola = Arrays.stream(QueueType.values())
            .collect(Collectors.toMap(
                qt -> qt,
                qt -> ticketRepository.findActiveByQueue(qt)
            ));
        
        // Obtener estadísticas de asesores
        Map<String, Object> estadisticasAsesores = advisorService.obtenerEstadisticas();
        
        DashboardResponse response = new DashboardResponse(
            ticketsPorCola,
            estadisticasAsesores,
            LocalDateTime.now()
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * Estado de una cola específica
     * 
     * GET /api/admin/queues/{queueType}
     */
    @GetMapping("/queues/{queueType}")
    public ResponseEntity<QueueStatusResponse> getQueueStatus(
        @PathVariable QueueType queueType
    ) {
        log.info("GET /api/admin/queues/{} - Obteniendo estado de cola", queueType);
        
        List<Ticket> activos = ticketRepository.findActiveByQueue(queueType);
        
        QueueStatusResponse response = new QueueStatusResponse(
            queueType,
            activos.size(),
            activos
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * Estadísticas detalladas de una cola
     * 
     * GET /api/admin/queues/{queueType}/stats
     */
    @GetMapping("/queues/{queueType}/stats")
    public ResponseEntity<QueueManagementService.QueueStats> getQueueStats(
        @PathVariable QueueType queueType
    ) {
        log.info("GET /api/admin/queues/{}/stats - Obteniendo estadísticas", queueType);
        
        QueueManagementService.QueueStats stats = 
            queueManagementService.obtenerEstadisticas(queueType);
        
        return ResponseEntity.ok(stats);
    }

    /**
     * Lista todos los asesores activos
     * 
     * GET /api/admin/advisors
     */
    @GetMapping("/advisors")
    public ResponseEntity<List<Advisor>> getAdvisors() {
        log.info("GET /api/admin/advisors - Obteniendo lista de asesores");
        
        List<Advisor> asesores = advisorService.obtenerAsesoresActivos();
        
        return ResponseEntity.ok(asesores);
    }

    /**
     * Estadísticas generales de asesores
     * 
     * GET /api/admin/advisors/stats
     */
    @GetMapping("/advisors/stats")
    public ResponseEntity<Map<String, Object>> getAdvisorStats() {
        log.info("GET /api/admin/advisors/stats - Obteniendo estadísticas de asesores");
        
        Map<String, Object> stats = advisorService.obtenerEstadisticas();
        
        return ResponseEntity.ok(stats);
    }

    /**
     * Cambia el estado de un asesor
     * 
     * PUT /api/admin/advisors/{id}/status?status=AVAILABLE
     */
    @PutMapping("/advisors/{id}/status")
    public ResponseEntity<String> updateAdvisorStatus(
        @PathVariable Long id,
        @RequestParam AdvisorStatus status
    ) {
        log.info("PUT /api/admin/advisors/{}/status - Cambiando estado a: {}",
            sanitize(id), sanitize(status));
        
        try {
            advisorService.cambiarEstado(id, status);
            
            return ResponseEntity.ok(
                String.format("Estado de asesor %d actualizado a %s", id, status)
            );
            
        } catch (Exception e) {
            log.error("Error actualizando estado de asesor {}: {}",
                sanitize(id), sanitize(e.getMessage()));
            return ResponseEntity
                .badRequest()
                .body("Error actualizando estado: " + sanitize(e.getMessage()));
        }
    }

    /**
     * Lista todos los tickets de todas las colas
     * 
     * GET /api/admin/tickets
     */
    @GetMapping("/tickets")
    public ResponseEntity<List<Ticket>> getAllTickets() {
        log.info("GET /api/admin/tickets - Obteniendo todos los tickets");
        
        List<Ticket> tickets = ticketRepository.findAll();
        
        return ResponseEntity.ok(tickets);
    }

    /**
     * Resumen ejecutivo del sistema
     * 
     * GET /api/admin/summary
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        log.info("GET /api/admin/summary - Obteniendo resumen ejecutivo");
        
        // Estadísticas por cola
        Map<String, QueueManagementService.QueueStats> statsPorCola = 
            Arrays.stream(QueueType.values())
                .collect(Collectors.toMap(
                    QueueType::name,
                    queueManagementService::obtenerEstadisticas
                ));
        
        // Estadísticas de asesores
        Map<String, Object> statsAsesores = advisorService.obtenerEstadisticas();
        
        // Total de tickets
        long totalTickets = ticketRepository.count();
        
        Map<String, Object> summary = Map.of(
            "timestamp", LocalDateTime.now(),
            "queueStats", statsPorCola,
            "advisorStats", statsAsesores,
            "totalTickets", totalTickets
        );
        
        return ResponseEntity.ok(summary);
    }

    // ========== ENDPOINTS FALTANTES IMPLEMENTADOS ==========

    /**
     * Llamar siguiente ticket en una cola específica
     * 
     * POST /api/admin/tickets/call-next/{queueType}
     */
    @PostMapping("/tickets/call-next/{queueType}")
    public ResponseEntity<TicketResponse> callNextTicket(@PathVariable QueueType queueType) {
        log.info("POST /api/admin/tickets/call-next/{} - Llamando siguiente ticket", queueType);
        
        try {
            var siguienteTicket = queueManagementService.obtenerSiguienteTicket(queueType);
            
            if (siguienteTicket.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            Ticket ticket = siguienteTicket.get();
            
            // Intentar asignar asesor
            var asesor = advisorService.obtenerYAsignarAsesor(ticket.getId(), queueType);
            
            if (asesor.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            
            // Enviar notificación Telegram
            notificationService.notificarTurnoActivo(ticket, asesor.get());
            
            // Actualizar posiciones en cola
            queueManagementService.actualizarPosicionesEnCola(queueType);
            
            // Convertir a TicketResponse
            TicketResponse response = ticketService.convertirATicketResponse(ticket);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error llamando siguiente ticket en cola {}: {}", queueType, sanitize(e.getMessage()));
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Completar un ticket específico
     * 
     * PUT /api/admin/tickets/{ticketId}/complete
     */
    @PutMapping("/tickets/{ticketId}/complete")
    public ResponseEntity<String> completeTicket(@PathVariable Long ticketId) {
        log.info("PUT /api/admin/tickets/{}/complete - Completando ticket", sanitize(ticketId));
        
        try {
            advisorService.completarAtencion(ticketId);
            
            // Actualizar posiciones en todas las colas (el ticket podría afectar cualquier cola)
            for (QueueType queueType : QueueType.values()) {
                queueManagementService.actualizarPosicionesEnCola(queueType);
            }
            
            return ResponseEntity.ok(String.format("Ticket %d completado exitosamente", ticketId));
            
        } catch (Exception e) {
            log.error("Error completando ticket {}: {}", sanitize(ticketId), sanitize(e.getMessage()));
            return ResponseEntity.badRequest().body("Error completando ticket: " + sanitize(e.getMessage()));
        }
    }

    /**
     * Iniciar atención de un ticket específico
     * 
     * PUT /api/admin/tickets/{ticketId}/start
     */
    @PutMapping("/tickets/{ticketId}/start")
    public ResponseEntity<String> startTicket(@PathVariable Long ticketId) {
        log.info("PUT /api/admin/tickets/{}/start - Iniciando atención", sanitize(ticketId));
        
        try {
            advisorService.iniciarAtencion(ticketId);
            
            return ResponseEntity.ok(String.format("Atención iniciada para ticket %d", ticketId));
            
        } catch (Exception e) {
            log.error("Error iniciando atención para ticket {}: {}", sanitize(ticketId), sanitize(e.getMessage()));
            return ResponseEntity.badRequest().body("Error iniciando atención: " + sanitize(e.getMessage()));
        }
    }

    /**
     * Crear un nuevo asesor
     * 
     * POST /api/admin/advisors
     */
    @PostMapping("/advisors")
    public ResponseEntity<Advisor> createAdvisor(@Valid @RequestBody AdvisorCreateRequest request) {
        log.info("POST /api/admin/advisors - Creando asesor: {}", sanitize(request.name()));
        
        try {
            Advisor nuevoAsesor = advisorService.crearAsesor(request);
            
            return ResponseEntity.ok(nuevoAsesor);
            
        } catch (Exception e) {
            log.error("Error creando asesor {}: {}", sanitize(request.name()), sanitize(e.getMessage()));
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Iniciar simulación automática
     * 
     * POST /api/admin/simulate/start
     */
    @PostMapping("/simulate/start")
    public ResponseEntity<String> startSimulation() {
        log.info("POST /api/admin/simulate/start - Iniciando simulación");
        
        if (simulationService.isSimulationRunning()) {
            return ResponseEntity.badRequest().body("Simulación ya está en ejecución");
        }
        
        simulationService.iniciarSimulacion();
        
        return ResponseEntity.ok("Simulación iniciada exitosamente");
    }

    /**
     * Detener simulación automática
     * 
     * POST /api/admin/simulate/stop
     */
    @PostMapping("/simulate/stop")
    public ResponseEntity<String> stopSimulation() {
        log.info("POST /api/admin/simulate/stop - Deteniendo simulación");
        
        String resultado = simulationService.detenerSimulacion();
        
        return ResponseEntity.ok(resultado);
    }

    /**
     * Estado de la simulación
     * 
     * GET /api/admin/simulate/status
     */
    @GetMapping("/simulate/status")
    public ResponseEntity<Map<String, Object>> getSimulationStatus() {
        boolean running = simulationService.isSimulationRunning();
        
        Map<String, Object> status = Map.of(
            "running", running,
            "timestamp", LocalDateTime.now()
        );
        
        return ResponseEntity.ok(status);
    }
}
