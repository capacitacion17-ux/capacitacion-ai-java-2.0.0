package com.example.ticketero.repository;

import com.example.ticketero.model.entity.QueueConfig;
import com.example.ticketero.model.enums.QueueType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QueueConfigRepository extends JpaRepository<QueueConfig, Long> {
    
    /**
     * Busca configuración por tipo de cola
     */
    Optional<QueueConfig> findByQueueType(QueueType queueType);
    
    /**
     * Busca todas las colas activas
     */
    List<QueueConfig> findByIsActiveTrue();
    
    /**
     * Busca colas ordenadas por prioridad
     */
    List<QueueConfig> findByIsActiveTrueOrderByPriorityAsc();
}
