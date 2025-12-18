package com.example.ticketero.controller;

import com.example.ticketero.model.entity.Advisor;
import com.example.ticketero.model.enums.QueueType;
import com.example.ticketero.service.AdvisorService;
import com.example.ticketero.service.QueueManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controlador para la interfaz web
 */
@Controller
@RequestMapping("/")
@RequiredArgsConstructor
public class WebController {

    private final QueueManagementService queueManagementService;
    private final AdvisorService advisorService;

    @GetMapping
    public String index() {
        return "index";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        // Estadísticas de colas
        Map<String, Object> queueStats = new HashMap<>();
        for (QueueType type : QueueType.values()) {
            var stats = queueManagementService.obtenerEstadisticas(type);
            queueStats.put(type.name().toLowerCase() + "Count", stats.waiting());
        }
        model.addAttribute("queueStats", queueStats);
        
        // Lista de asesores
        model.addAttribute("advisors", advisorService.obtenerAsesoresActivos());
        
        // Tipos de cola disponibles
        model.addAttribute("queueTypes", Arrays.asList(QueueType.values()));
        
        return "dashboard";
    }

    @GetMapping("/create-ticket")
    public String createTicket() {
        return "create-ticket";
    }

    @GetMapping("/check-ticket")
    public String checkTicket() {
        return "check-ticket";
    }
    
    @GetMapping("/api/advisors")
    @ResponseBody
    public List<Advisor> getAdvisors() {
        return advisorService.obtenerAsesoresActivos();
    }
}