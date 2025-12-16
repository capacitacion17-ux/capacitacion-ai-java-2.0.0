package com.example.ticketero.service;

import com.example.ticketero.model.enums.QueueType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Servicio para métricas custom de negocio.
 * Expone métricas en formato Prometheus.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsService {

    private final MeterRegistry registry;

    /**
     * Incrementa contador de tickets creados por cola.
     */
    public void incrementTicketsCreated(QueueType queueType) {
        Counter.builder("tickets.created")
                .tag("queue", queueType.name())
                .description("Total de tickets creados por cola")
                .register(registry)
                .increment();
        
        log.debug("Métrica: tickets.created incrementada para cola {}", queueType);
    }

    /**
     * Incrementa contador de tickets completados por cola.
     */
    public void incrementTicketsCompleted(QueueType queueType) {
        Counter.builder("tickets.completed")
                .tag("queue", queueType.name())
                .description("Total de tickets completados por cola")
                .register(registry)
                .increment();
    }

    /**
     * Registra tiempo de procesamiento de un ticket.
     */
    public void recordProcessingTime(QueueType queueType, long millis) {
        Timer.builder("tickets.processing.time")
                .tag("queue", queueType.name())
                .description("Tiempo de procesamiento de tickets")
                .register(registry)
                .record(millis, TimeUnit.MILLISECONDS);
        
        log.debug("Métrica: tickets.processing.time={} ms para cola {}", millis, queueType);
    }

    /**
     * Incrementa contador de notificaciones enviadas.
     */
    public void incrementNotificationsSent(String type) {
        Counter.builder("notifications.sent")
                .tag("type", type)
                .description("Total de notificaciones enviadas por tipo")
                .register(registry)
                .increment();
    }

    /**
     * Incrementa contador de errores.
     */
    public void incrementErrors(String component) {
        Counter.builder("errors.total")
                .tag("component", component)
                .description("Total de errores por componente")
                .register(registry)
                .increment();
    }
}
