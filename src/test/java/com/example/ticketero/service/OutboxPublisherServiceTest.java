package com.example.ticketero.service;

import com.example.ticketero.model.dto.TicketQueueMessage;
import com.example.ticketero.model.entity.OutboxMessage;
import com.example.ticketero.repository.OutboxMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static com.example.ticketero.testutil.TestDataBuilder.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxPublisherService - Unit Tests")
class OutboxPublisherServiceTest {

    @Mock
    private OutboxMessageRepository outboxRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OutboxPublisherService outboxPublisherService;

    @Nested
    @DisplayName("processOutbox()")
    class ProcessOutbox {

        @Test
        @DisplayName("con mensaje pendiente → debe publicar y marcar SENT")
        void processOutbox_conMensajePendiente_debePublicarYMarcarSent() {
            // Given
            ReflectionTestUtils.setField(outboxPublisherService, "exchangeName", "ticketero-exchange");
            OutboxMessage mensaje = outboxPending().build();
            when(outboxRepository.findPendingWithLock(any(), any()))
                .thenReturn(List.of(mensaje));

            // When
            outboxPublisherService.processOutbox();

            // Then
            verify(rabbitTemplate).convertAndSend(
                anyString(),
                eq("caja-queue"),
                any(TicketQueueMessage.class),
                any()
            );
            verify(outboxRepository).markAsSent(eq(1L), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("sin mensajes pendientes → no debe hacer nada")
        void processOutbox_sinMensajes_noDebeHacerNada() {
            // Given
            when(outboxRepository.findPendingWithLock(any(), any()))
                .thenReturn(List.of());

            // When
            outboxPublisherService.processOutbox();

            // Then
            verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class), any());
            verify(outboxRepository, never()).markAsSent(any(), any());
        }

        @Test
        @DisplayName("fallo al publicar → debe incrementar retry y programar siguiente")
        void processOutbox_falloAlPublicar_debeIncrementarRetry() {
            // Given
            ReflectionTestUtils.setField(outboxPublisherService, "exchangeName", "ticketero-exchange");
            OutboxMessage mensaje = outboxPending().retryCount(0).maxRetries(5).build();
            when(outboxRepository.findPendingWithLock(any(), any()))
                .thenReturn(List.of(mensaje));
            doThrow(new RuntimeException("RabbitMQ error"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class), any());

            // When
            outboxPublisherService.processOutbox();

            // Then
            verify(outboxRepository).scheduleRetry(
                eq(1L),
                eq(1), // retryCount incrementado
                any(LocalDateTime.class), // nextRetryAt
                contains("RabbitMQ error")
            );
            verify(outboxRepository, never()).markAsSent(any(), any());
        }

        @Test
        @DisplayName("reintentos agotados → debe marcar FAILED")
        void processOutbox_reintentosAgotados_debeMarcarFailed() {
            // Given
            ReflectionTestUtils.setField(outboxPublisherService, "exchangeName", "ticketero-exchange");
            OutboxMessage mensaje = outboxPending()
                .retryCount(4)
                .maxRetries(5)
                .build();
            when(outboxRepository.findPendingWithLock(any(), any()))
                .thenReturn(List.of(mensaje));
            doThrow(new RuntimeException("Error"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class), any());

            // When
            outboxPublisherService.processOutbox();

            // Then
            verify(outboxRepository).markAsFailed(
                eq(1L),
                anyString(),
                any(LocalDateTime.class)
            );
        }

        @Test
        @DisplayName("debe parsear JSON a TicketQueueMessage correctamente")
        void processOutbox_debeParserJsonCorrectamente() {
            // Given
            ReflectionTestUtils.setField(outboxPublisherService, "exchangeName", "ticketero-exchange");
            String payload = "{\"ticketId\":99,\"numero\":\"C099\",\"queueType\":\"CAJA\",\"telefono\":\"+56912345678\"}";
            OutboxMessage mensaje = outboxPending().payload(payload).build();
            when(outboxRepository.findPendingWithLock(any(), any()))
                .thenReturn(List.of(mensaje));

            // When
            outboxPublisherService.processOutbox();

            // Then
            ArgumentCaptor<TicketQueueMessage> captor = ArgumentCaptor.forClass(TicketQueueMessage.class);
            verify(rabbitTemplate).convertAndSend(anyString(), anyString(), captor.capture(), any());

            TicketQueueMessage parsed = captor.getValue();
            assertThat(parsed.ticketId()).isEqualTo(99L);
            assertThat(parsed.numero()).isEqualTo("C099");
        }
    }
}