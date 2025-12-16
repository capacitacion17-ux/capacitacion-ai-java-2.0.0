package com.example.ticketero.repository;

import com.example.ticketero.model.entity.OutboxMessage;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repositorio para mensajes del patrón Outbox.
 * Incluye queries optimizadas para procesamiento batch con bloqueo pesimista.
 */
@Repository
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {

    /**
     * Busca mensajes pendientes de envío con bloqueo pesimista.
     * SELECT FOR UPDATE garantiza que solo un scheduler procese cada mensaje.
     *
     * @param pageable Límite de mensajes a procesar (ej: PageRequest.of(0, 50))
     * @return Lista de mensajes pendientes
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT o FROM OutboxMessage o
        WHERE o.status = 'PENDING'
        AND (o.nextRetryAt IS NULL OR o.nextRetryAt <= :now)
        ORDER BY o.createdAt ASC
        """)
    List<OutboxMessage> findPendingWithLock(
        @Param("now") LocalDateTime now,
        Pageable pageable
    );

    /**
     * Marca un mensaje como enviado exitosamente.
     *
     * @param id ID del mensaje
     * @param processedAt Timestamp de procesamiento
     */
    @Modifying
    @Query("""
        UPDATE OutboxMessage o
        SET o.status = 'SENT', o.processedAt = :processedAt
        WHERE o.id = :id
        """)
    void markAsSent(@Param("id") Long id, @Param("processedAt") LocalDateTime processedAt);

    /**
     * Marca un mensaje como fallido permanentemente.
     *
     * @param id ID del mensaje
     * @param errorMessage Mensaje de error
     * @param processedAt Timestamp de procesamiento
     */
    @Modifying
    @Query("""
        UPDATE OutboxMessage o
        SET o.status = 'FAILED', o.errorMessage = :errorMessage, o.processedAt = :processedAt
        WHERE o.id = :id
        """)
    void markAsFailed(
        @Param("id") Long id,
        @Param("errorMessage") String errorMessage,
        @Param("processedAt") LocalDateTime processedAt
    );

    /**
     * Incrementa el contador de reintentos y programa el próximo intento.
     *
     * @param id ID del mensaje
     * @param retryCount Nuevo contador de reintentos
     * @param nextRetryAt Próximo intento programado
     * @param errorMessage Mensaje de error del intento fallido
     */
    @Modifying
    @Query("""
        UPDATE OutboxMessage o
        SET o.retryCount = :retryCount,
            o.nextRetryAt = :nextRetryAt,
            o.errorMessage = :errorMessage,
            o.status = 'PENDING'
        WHERE o.id = :id
        """)
    void scheduleRetry(
        @Param("id") Long id,
        @Param("retryCount") Integer retryCount,
        @Param("nextRetryAt") LocalDateTime nextRetryAt,
        @Param("errorMessage") String errorMessage
    );

    /**
     * Cuenta mensajes pendientes (para monitoreo).
     *
     * @return Número de mensajes pendientes
     */
    @Query("SELECT COUNT(o) FROM OutboxMessage o WHERE o.status = 'PENDING'")
    long countPending();

    /**
     * Cuenta mensajes fallidos (para alertas).
     *
     * @return Número de mensajes fallidos
     */
    @Query("SELECT COUNT(o) FROM OutboxMessage o WHERE o.status = 'FAILED'")
    long countFailed();

    /**
     * Limpia mensajes enviados antiguos (para mantenimiento).
     *
     * @param before Fecha límite
     * @return Número de mensajes eliminados
     */
    @Modifying
    @Query("DELETE FROM OutboxMessage o WHERE o.status = 'SENT' AND o.processedAt < :before")
    int deleteOldSentMessages(@Param("before") LocalDateTime before);

    /**
     * Busca mensajes por agregado (para debugging).
     *
     * @param aggregateType Tipo de agregado
     * @param aggregateId ID del agregado
     * @return Lista de mensajes del agregado
     */
    List<OutboxMessage> findByAggregateTypeAndAggregateIdOrderByCreatedAtDesc(
        String aggregateType,
        Long aggregateId
    );
}
