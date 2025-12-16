package com.example.ticketero.service;

import com.example.ticketero.model.entity.Advisor;
import com.example.ticketero.model.enums.AdvisorStatus;
import com.example.ticketero.repository.AdvisorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Servicio para gestión de heartbeat de workers.
 * Actualiza el heartbeat de asesores BUSY cada 5 segundos.
 * 
 * El heartbeat permite detectar workers muertos y recuperar automáticamente.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HeartbeatService {

    private final AdvisorRepository advisorRepository;

    /**
     * Actualiza heartbeat de todos los asesores BUSY.
     * Se ejecuta cada 5 segundos.
     * 
     * En producción real, cada worker actualizaría su propio heartbeat.
     * Para simplicidad, este servicio centralizado actualiza todos.
     */
    @Scheduled(fixedDelay = 5000) // Cada 5 segundos
    @Transactional
    public void actualizarHeartbeats() {
        List<Advisor> busyAdvisors = advisorRepository.findByStatus(AdvisorStatus.BUSY);
        
        if (!busyAdvisors.isEmpty()) {
            log.debug("Actualizando heartbeat de {} asesores ocupados", busyAdvisors.size());
            
            for (Advisor advisor : busyAdvisors) {
                advisorRepository.updateHeartbeat(advisor.getId());
            }
        }
    }
}
