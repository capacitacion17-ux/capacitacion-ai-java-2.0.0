package com.example.ticketero.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Entidad para el patrón Outbox.
 * Garantiza consistencia transaccional entre PostgreSQL y RabbitMQ.
 *
 * Flujo:
 * 1. Se guarda en la misma TX que los datos de negocio (status=PENDING)
 * 2. OutboxPublisherService lee mensajes PENDING y los envía a RabbitMQ
 * 3. Si el envío es exitoso, marca como SENT
 * 4. Si falla, incrementa retry_count hasta max_retries, luego FAILED
 */
@Entity
@Table(name = "outbox_message")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Tipo de agregado origen (ej: "TICKET", "ADVISOR").
     */
    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    /**
     * ID del agregado origen (ej: ticket.id).
     */
    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    /**
     * Tipo de evento (ej: "TICKET_CREATED", "TICKET_UPDATED").
     */
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    /**
     * Contenido del mensaje en formato JSON.
     * Almacenado como JSONB en PostgreSQL para eficiencia.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    /**
     * Routing key para RabbitMQ (nombre de la cola destino).
     */
    @Column(name = "routing_key", nullable = false, length = 100)
    private String routingKey;

    /**
     * Estado del mensaje: PENDING, PROCESSING, SENT, FAILED.
     */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    /**
     * Timestamp de creación.
     */
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Timestamp de procesamiento exitoso.
     */
    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    /**
     * Contador de reintentos.
     */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * Máximo de reintentos antes de marcar como FAILED.
     */
    @Column(name = "max_retries", nullable = false)
    @Builder.Default
    private Integer maxRetries = 5;

    /**
     * Próximo intento programado (para backoff exponencial).
     */
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    /**
     * Mensaje de error del último intento fallido.
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Incrementa el contador de reintentos y calcula próximo intento.
     * Usa backoff exponencial: 1s, 2s, 4s, 8s, 16s.
     */
    public void incrementRetryAndScheduleNext() {
        this.retryCount++;
        long delaySeconds = (long) Math.pow(2, this.retryCount - 1);
        this.nextRetryAt = LocalDateTime.now().plusSeconds(delaySeconds);
    }

    /**
     * Verifica si se han agotado los reintentos.
     *
     * @return true si retry_count >= max_retries
     */
    public boolean isRetriesExhausted() {
        return this.retryCount >= this.maxRetries;
    }

    /**
     * Marca el mensaje como enviado exitosamente.
     */
    public void markAsSent() {
        this.status = "SENT";
        this.processedAt = LocalDateTime.now();
    }

    /**
     * Marca el mensaje como fallido permanentemente.
     *
     * @param error Mensaje de error
     */
    public void markAsFailed(String error) {
        this.status = "FAILED";
        this.errorMessage = error;
        this.processedAt = LocalDateTime.now();
    }

    /**
     * Marca el mensaje como en procesamiento.
     */
    public void markAsProcessing() {
        this.status = "PROCESSING";
    }
}
