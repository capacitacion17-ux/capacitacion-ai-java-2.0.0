package com.example.ticketero.repository;

import com.example.ticketero.model.entity.Advisor;
import com.example.ticketero.model.enums.AdvisorStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AdvisorRepository extends JpaRepository<Advisor, Long> {
    
    /**
     * Busca asesores por estado
     */
    List<Advisor> findByStatus(AdvisorStatus status);
    
    /**
     * Busca asesores disponibles para un tipo de cola específico.
     * Ordena por cantidad de tickets atendidos (para balancear carga).
     *
     * NOTA: Sin bloqueo - usar findAvailableForQueueWithLock para concurrencia.
     */
    @Query("""
        SELECT a FROM Advisor a
        WHERE a.status = com.example.ticketero.model.enums.AdvisorStatus.AVAILABLE
        AND a.queueTypesJson LIKE CONCAT('%', :queueType, '%')
        ORDER BY a.totalTicketsServed ASC
        """)
    List<Advisor> findAvailableForQueue(@Param("queueType") String queueType);

    /**
     * Busca asesores disponibles CON BLOQUEO PESIMISTA.
     *
     * FIX RACE CONDITION: SELECT ... FOR UPDATE garantiza que solo un worker
     * pueda leer y asignar un advisor a la vez. Otros workers esperan.
     *
     * IMPORTANTE: Debe usarse dentro de @Transactional para mantener el lock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT a FROM Advisor a
        WHERE a.status = com.example.ticketero.model.enums.AdvisorStatus.AVAILABLE
        AND a.queueTypesJson LIKE CONCAT('%', :queueType, '%')
        ORDER BY a.totalTicketsServed ASC
        """)
    List<Advisor> findAvailableForQueueWithLock(@Param("queueType") String queueType);
    
    /**
     * Busca asesor por número de módulo
     */
    Optional<Advisor> findByModuleNumber(Integer moduleNumber);
    
    /**
     * Busca asesores activos (no OFFLINE)
     */
    @Query("SELECT a FROM Advisor a WHERE a.status != 'OFFLINE'")
    List<Advisor> findActiveAdvisors();

    /**
     * Actualiza el heartbeat de un asesor específico.
     * Se ejecuta cada 5 segundos por worker activo
     *
     * @param advisorId id del asesor
     */
    @Modifying
    @Query("UPDATE Advisor a SET a.lastHeartbeat = CURRENT_TIMESTAMP WHERE a.id = :advisorId")
    void updateHeartbeat(@Param("advisorId") Long advisorId);

    /**
     * Encuentra asesores con heartbeat vencido (posibles workers muertos).
     * Se considera muerto si last_heartbeat > threshold
     *
     * @param threshold tiempo límite (ej: NOW() - 60 segundos)
     * @return lista de asesores potencialmente muertos
     */
    @Query("""
        SELECT a FROM Advisor a 
        WHERE a.status = com.example.ticketero.model.enums.AdvisorStatus.BUSY
        AND (a.lastHeartbeat IS NULL OR a.lastHeartbeat < :threshold)
        """)
    List<Advisor> findDeadWorkers(@Param("threshold") LocalDateTime threshold);

    /**
     * Incrementa el contador de recuperaciones de un asesor.
     *
     * @param advisorId id del asesor
     */
    @Modifying
    @Query("UPDATE Advisor a SET a.recoveryCount = a.recoveryCount + 1 WHERE a.id = :advisorId")
    void incrementRecoveryCount(@Param("advisorId") Long advisorId);
}
