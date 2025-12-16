package com.example.ticketero.controller;

import com.example.ticketero.model.dto.DashboardResponse;
import com.example.ticketero.model.dto.QueueStatusResponse;
import com.example.ticketero.model.entity.Advisor;
import com.example.ticketero.model.entity.Ticket;
import com.example.ticketero.model.enums.AdvisorStatus;
import com.example.ticketero.model.enums.QueueType;
import com.example.ticketero.repository.TicketRepository;
import com.example.ticketero.service.AdvisorService;
import com.example.ticketero.service.QueueManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
