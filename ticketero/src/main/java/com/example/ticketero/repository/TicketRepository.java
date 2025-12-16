package com.example.ticketero.repository;

import com.example.ticketero.model.entity.Ticket;
import com.example.ticketero.model.enums.QueueType;
import com.example.ticketero.model.enums.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    /**
     * Busca ticket por código de referencia
     */
    Optional<Ticket> findByCodigoReferencia(UUID codigoReferencia);

    /**
     * Busca ticket por ID nacional
     */
    Optional<Ticket> findByNationalId(String nationalId);
    
    /**
     * Busca ticket por número
     */
    Optional<Ticket> findByNumero(String numero);
    
    /**
     * Busca tickets por cola y estado específico
     */
    @Query("""
        SELECT t FROM Ticket t 
        WHERE t.queueType = :queueType 
        AND t.status = :status 
        ORDER BY t.createdAt ASC
        """)
    List<Ticket> findByQueueAndStatus(
        @Param("queueType") QueueType queueType,
        @Param("status") TicketStatus status
    );
    
    /**
     * Cuenta tickets en espera creados antes de una fecha
     * Usado para calcular posición en cola
     */
    @Query("""
        SELECT COUNT(t) FROM Ticket t 
        WHERE t.queueType = :queueType 
        AND t.status = 'WAITING' 
        AND t.createdAt < :before
        """)
    int countWaitingBefore(
        @Param("queueType") QueueType queueType,
        @Param("before") LocalDateTime before
    );
    
    /**
     * Busca tickets activos de una cola (WAITING o CALLED)
     * Ordenados por posición
     */
    @Query("""
        SELECT t FROM Ticket t 
        WHERE t.queueType = :queueType 
        AND t.status IN ('WAITING', 'CALLED') 
        ORDER BY t.positionInQueue ASC
        """)
    List<Ticket> findActiveByQueue(@Param("queueType") QueueType queueType);
    
    /**
     * Busca todos los tickets de una cola
     */
    List<Ticket> findByQueueType(QueueType queueType);
    
    /**
     * Busca tickets creados en un rango de fechas
     */
    List<Ticket> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    
    /**
     * Cuenta tickets por estado
     */
    long countByStatus(TicketStatus status);
    
    /**
     * Cuenta tickets por cola y estado
     */
    long countByQueueTypeAndStatus(QueueType queueType, TicketStatus status);

    /**
     * Actualiza estado y startedAt directamente (evita lazy loading issues).
     * FIX: No toca assigned_advisor_id.
     */
    @Modifying
    @Query("UPDATE Ticket t SET t.status = :status, t.startedAt = :startedAt WHERE t.id = :ticketId")
    void updateStatusAndStartedAt(
        @Param("ticketId") Long ticketId,
        @Param("status") TicketStatus status,
        @Param("startedAt") LocalDateTime startedAt
    );

    /**
     * Actualiza estado y completedAt directamente (evita lazy loading issues).
     * FIX: No toca assigned_advisor_id.
     */
    @Modifying
    @Query("UPDATE Ticket t SET t.status = :status, t.completedAt = :completedAt WHERE t.id = :ticketId")
    void updateStatusAndCompletedAt(
        @Param("ticketId") Long ticketId,
        @Param("status") TicketStatus status,
        @Param("completedAt") LocalDateTime completedAt
    );

    /**
     * FIX DEFINITIVO: Asigna advisor usando UPDATE directo.
     * Evita TODOS los problemas de Hibernate con relaciones lazy.
     */
    @Modifying
    @Query("""
        UPDATE Ticket t SET
            t.status = :status,
            t.calledAt = :calledAt,
            t.assignedModuleNumber = :moduleNumber,
            t.assignedAdvisor.id = :advisorId
        WHERE t.id = :ticketId
        """)
    void assignAdvisor(
        @Param("ticketId") Long ticketId,
        @Param("advisorId") Long advisorId,
        @Param("status") TicketStatus status,
        @Param("calledAt") LocalDateTime calledAt,
        @Param("moduleNumber") Integer moduleNumber
    );

    /**
     * Obtiene el ticket actual de un asesor (CALLED o IN_PROGRESS).
     * Reemplaza la relación circular Advisor.currentTicket.
     */
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.assignedAdvisor.id = :advisorId
        AND t.status IN ('CALLED', 'IN_PROGRESS')
        """)
    Optional<Ticket> findCurrentTicketForAdvisor(@Param("advisorId") Long advisorId);
}
