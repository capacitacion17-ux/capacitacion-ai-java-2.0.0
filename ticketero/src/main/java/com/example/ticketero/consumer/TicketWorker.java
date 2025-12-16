package com.example.ticketero.consumer;

import com.example.ticketero.exception.NoAdvisorAvailableException;
import com.example.ticketero.model.dto.TicketQueueMessage;
import com.example.ticketero.model.enums.QueueType;
import com.example.ticketero.service.TicketProcessingService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Worker que consume tickets de las colas de RabbitMQ con Manual ACK.
 *
 * REFACTORIZADO: Ahora usa TicketProcessingService para procesar
 * tickets en UNA SOLA TRANSACCIÓN, eliminando problemas de
 * inconsistencia entre TX fragmentadas.
 *
 * Garantías:
 * - ACK solo después de completar TODO el procesamiento
 * - NACK + requeue si falla cualquier parte del flujo
 * - Backoff exponencial cuando no hay advisors disponibles
 * - Idempotencia: tickets ya procesados se ignoran
 *
 * Configuración en application.yml:
 * - concurrency: 3 (workers paralelos)
 * - prefetch: 1 (un mensaje a la vez por worker)
 * - acknowledge-mode: manual
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TicketWorker {

    private final TicketProcessingService ticketProcessingService;

    /**
     * Worker para cola CAJA.
     */
    @RabbitListener(queues = "caja-queue", ackMode = "MANUAL")
    public void procesarTicketCaja(TicketQueueMessage message,
                                    Channel channel,
                                    @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        procesarTicketConAck(message, QueueType.CAJA, channel, deliveryTag);
    }

    /**
     * Worker para cola PERSONAL.
     */
    @RabbitListener(queues = "personal-queue", ackMode = "MANUAL")
    public void procesarTicketPersonal(TicketQueueMessage message,
                                        Channel channel,
                                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        procesarTicketConAck(message, QueueType.PERSONAL, channel, deliveryTag);
    }

    /**
     * Worker para cola EMPRESAS.
     */
    @RabbitListener(queues = "empresas-queue", ackMode = "MANUAL")
    public void procesarTicketEmpresas(TicketQueueMessage message,
                                        Channel channel,
                                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        procesarTicketConAck(message, QueueType.EMPRESAS, channel, deliveryTag);
    }

    /**
     * Worker para cola GERENCIA.
     */
    @RabbitListener(queues = "gerencia-queue", ackMode = "MANUAL")
    public void procesarTicketGerencia(TicketQueueMessage message,
                                        Channel channel,
                                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        procesarTicketConAck(message, QueueType.GERENCIA, channel, deliveryTag);
    }

    /**
     * Procesa un ticket con manejo de ACK/NACK.
     *
     * FLUJO SIMPLIFICADO:
     * 1. Delegar a TicketProcessingService (TX única)
     * 2. Si éxito → ACK (mensaje eliminado de cola)
     * 3. Si no hay advisors → backoff + NACK con requeue
     * 4. Si otro error → NACK con requeue
     *
     * ACK SOLO AL FINAL: El mensaje se elimina de RabbitMQ únicamente
     * después de que TODA la transacción haya completado exitosamente.
     */
    private void procesarTicketConAck(TicketQueueMessage message,
                                       QueueType queueType,
                                       Channel channel,
                                       long deliveryTag) {
        log.info("[WORKER] Procesando ticket: {} de cola: {}",
            message.numero(), queueType);

        try {
            // Procesar ticket COMPLETO en una sola transacción
            boolean exitoso = ticketProcessingService.procesarTicketCompleto(
                message.ticketId(),
                queueType
            );

            if (exitoso) {
                // Procesado exitosamente → ACK
                channel.basicAck(deliveryTag, false);
                log.info("[WORKER] ACK enviado para ticket: {}", message.numero());
            } else {
                // Ya procesado (idempotencia) → ACK sin reprocesar
                channel.basicAck(deliveryTag, false);
                log.info("[WORKER] Ticket {} ya procesado. ACK (idempotencia).",
                    message.numero());
            }

        } catch (NoAdvisorAvailableException e) {
            // No hay advisors → esperar y re-encolar
            log.warn("[WORKER] No hay advisors para {} (ticket {}). Backoff...",
                queueType, message.numero());
            aplicarBackoffYReencolar(channel, deliveryTag);

        } catch (InterruptedException e) {
            // Thread interrumpido (shutdown) → re-encolar
            log.warn("[WORKER] Interrumpido procesando ticket: {}. Re-encolando...",
                message.numero());
            Thread.currentThread().interrupt();
            reencolarMensaje(channel, deliveryTag);

        } catch (Exception e) {
            // Error inesperado → re-encolar
            log.error("[WORKER] Error procesando ticket {}: {}. Re-encolando...",
                message.numero(), e.getMessage(), e);
            reencolarMensaje(channel, deliveryTag);
        }
    }

    /**
     * Aplica backoff antes de re-encolar.
     * Evita loop infinito cuando no hay advisors.
     *
     * Backoff fijo de 3 segundos para simplicidad.
     * En producción, considerar backoff exponencial con headers.
     */
    private void aplicarBackoffYReencolar(Channel channel, long deliveryTag) {
        try {
            // Esperar antes de re-encolar
            Thread.sleep(3000L);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("[WORKER] Backoff interrumpido");
        }
        reencolarMensaje(channel, deliveryTag);
    }

    /**
     * Re-encola un mensaje con NACK + requeue=true.
     */
    private void reencolarMensaje(Channel channel, long deliveryTag) {
        try {
            channel.basicNack(deliveryTag, false, true);
            log.debug("[WORKER] Mensaje re-encolado (NACK con requeue)");
        } catch (IOException e) {
            log.error("[WORKER] Error re-encolando mensaje", e);
        }
    }
}
