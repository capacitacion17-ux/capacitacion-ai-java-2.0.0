package com.example.ticketero.repository;

import com.example.ticketero.model.entity.TicketEvent;
import com.example.ticketero.model.enums.EventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TicketEventRepository extends JpaRepository<TicketEvent, Long> {
    
    /**
     * Busca eventos de un ticket ordenados por fecha descendente
     */
    List<TicketEvent> findByTicketIdOrderByCreatedAtDesc(Long ticketId);
    
    /**
     * Busca eventos recientes por tipo
     */
    @Query("""
        SELECT e FROM TicketEvent e 
        WHERE e.eventType = :eventType 
        AND e.createdAt >= :since
        ORDER BY e.createdAt DESC
        """)
    List<TicketEvent> findRecentByType(
        @Param("eventType") EventType eventType,
        @Param("since") LocalDateTime since
    );
    
    /**
     * Busca todos los eventos de un ticket
     */
    List<TicketEvent> findByTicketId(Long ticketId);
}
