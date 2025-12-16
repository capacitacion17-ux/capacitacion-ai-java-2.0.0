package com.example.ticketero.repository;

import com.example.ticketero.model.entity.RecoveryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repositorio para eventos de recuperación automática.
 */
@Repository
public interface RecoveryEventRepository extends JpaRepository<RecoveryEvent, Long> {

    /**
     * Encuentra eventos de recuperación por tipo.
     *
     * @param recoveryType tipo de recuperación (DEAD_WORKER, TIMEOUT, MANUAL)
     * @return lista de eventos
     */
    List<RecoveryEvent> findByRecoveryTypeOrderByDetectedAtDesc(String recoveryType);

    /**
     * Encuentra eventos recientes (últimas 24 horas).
     *
     * @param since desde cuándo buscar
     * @return lista de eventos
     */
    List<RecoveryEvent> findByDetectedAtAfterOrderByDetectedAtDesc(LocalDateTime since);

    /**
     * Cuenta recuperaciones por asesor.
     *
     * @param advisorId id del asesor
     * @return número de recuperaciones
     */
    long countByAdvisorId(Long advisorId);
}
