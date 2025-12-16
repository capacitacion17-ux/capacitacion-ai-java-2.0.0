package com.example.ticketero.service;

import com.example.ticketero.model.dto.TicketQueueMessage;
import com.example.ticketero.model.entity.OutboxMessage;
import com.example.ticketero.repository.OutboxMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Servicio que implementa el patrón Outbox.
 * Lee mensajes pendientes de la tabla outbox_message y los publica a RabbitMQ.
 *
 * Garantías:
 * - Atomicidad: El mensaje se guarda en DB en la misma TX que los datos de negocio
 * - Durabilidad: Si RabbitMQ falla, el mensaje queda en DB para reintento
 * - Idempotencia: SELECT FOR UPDATE previene procesamiento duplicado
 *
 * Flujo:
 * 1. Cada segundo, lee hasta 50 mensajes PENDING con bloqueo pesimista
 * 2. Para cada mensaje, intenta publicar a RabbitMQ
 * 3. Si éxito: marca como SENT
 * 4. Si falla: incrementa retry_count con backoff exponencial
 * 5. Si reintentos agotados: marca como FAILED (requiere intervención manual)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherService {

    private final OutboxMessageRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.rabbitmq.exchange}")
    private String exchangeName;

    /**
     * Tamaño del batch de mensajes a procesar por ciclo.
     */
    private static final int BATCH_SIZE = 50;

    /**
     * Procesa mensajes pendientes del outbox.
     * Se ejecuta cada segundo para baja latencia.
     */
    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void processOutbox() {
        LocalDateTime now = LocalDateTime.now();

        // Obtener mensajes pendientes con bloqueo (SELECT FOR UPDATE)
        List<OutboxMessage> pendingMessages = outboxRepository.findPendingWithLock(
            now,
            PageRequest.of(0, BATCH_SIZE)
        );

        if (pendingMessages.isEmpty()) {
            return;
        }

        log.debug("Procesando {} mensajes del outbox", pendingMessages.size());

        int sent = 0;
        int failed = 0;

        for (OutboxMessage message : pendingMessages) {
            try {
                publishToRabbitMQ(message);
                outboxRepository.markAsSent(message.getId(), LocalDateTime.now());
                sent++;
                log.debug("Mensaje outbox {} enviado a {}", message.getId(), message.getRoutingKey());

            } catch (Exception e) {
                handlePublishFailure(message, e);
                failed++;
            }
        }

        if (sent > 0 || failed > 0) {
            log.info("Outbox procesado: {} enviados, {} fallidos", sent, failed);
        }
    }

    /**
     * Publica un mensaje a RabbitMQ.
     *
     * FIX: Parsear el payload JSON a objeto antes de enviar.
     * RabbitTemplate con Jackson2JsonMessageConverter lo serializará correctamente.
     * Antes: enviaba String JSON que se re-serializaba (doble encoding).
     *
     * @param message Mensaje del outbox
     */
    private void publishToRabbitMQ(OutboxMessage message) {
        try {
            // Parsear JSON string a objeto TicketQueueMessage
            TicketQueueMessage payload = objectMapper.readValue(
                message.getPayload(),
                TicketQueueMessage.class
            );

            rabbitTemplate.convertAndSend(
                exchangeName,
                message.getRoutingKey(),
                payload,  // Enviar objeto, no String
                msg -> {
                    msg.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    msg.getMessageProperties().setHeader("outbox_id", message.getId());
                    msg.getMessageProperties().setHeader("event_type", message.getEventType());
                    return msg;
                }
            );
        } catch (Exception e) {
            throw new RuntimeException("Error parseando payload outbox: " + e.getMessage(), e);
        }
    }

    /**
     * Maneja un fallo de publicación.
     * Implementa backoff exponencial antes del próximo reintento.
     *
     * @param message Mensaje que falló
     * @param e Excepción que causó el fallo
     */
    private void handlePublishFailure(OutboxMessage message, Exception e) {
        String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();

        log.warn("Error publicando mensaje outbox {}: {}", message.getId(), errorMsg);

        // Incrementar retry y verificar si se agotaron
        int newRetryCount = message.getRetryCount() + 1;

        if (newRetryCount >= message.getMaxRetries()) {
            // Reintentos agotados - marcar como FAILED
            outboxRepository.markAsFailed(message.getId(), errorMsg, LocalDateTime.now());
            log.error("Mensaje outbox {} marcado como FAILED después de {} intentos",
                message.getId(), newRetryCount);
        } else {
            // Programar próximo reintento con backoff exponencial
            long delaySeconds = (long) Math.pow(2, newRetryCount - 1); // 1s, 2s, 4s, 8s, 16s
            LocalDateTime nextRetry = LocalDateTime.now().plusSeconds(delaySeconds);

            outboxRepository.scheduleRetry(
                message.getId(),
                newRetryCount,
                nextRetry,
                errorMsg
            );

            log.info("Mensaje outbox {} programado para reintento #{} en {}s",
                message.getId(), newRetryCount, delaySeconds);
        }
    }

    /**
     * Limpia mensajes enviados antiguos.
     * Se ejecuta diariamente a las 3:00 AM.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupOldMessages() {
        // Eliminar mensajes SENT de más de 7 días
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        int deleted = outboxRepository.deleteOldSentMessages(cutoff);

        if (deleted > 0) {
            log.info("Limpieza de outbox: {} mensajes antiguos eliminados", deleted);
        }
    }

    /**
     * Obtiene estadísticas del outbox para monitoreo.
     *
     * @return Estadísticas como String
     */
    public String getStats() {
        long pending = outboxRepository.countPending();
        long failed = outboxRepository.countFailed();
        return String.format("Outbox stats - Pending: %d, Failed: %d", pending, failed);
    }
}
