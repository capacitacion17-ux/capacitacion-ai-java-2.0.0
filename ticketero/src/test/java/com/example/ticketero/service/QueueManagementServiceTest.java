package com.example.ticketero.service;

import com.example.ticketero.model.entity.*;
import com.example.ticketero.model.enums.*;
import com.example.ticketero.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static com.example.ticketero.testutil.TestDataBuilder.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueueManagementService - Unit Tests")
class QueueManagementServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private QueueConfigRepository queueConfigRepository;

    @Mock
    private TicketEventRepository ticketEventRepository;

    @InjectMocks
    private QueueManagementService queueManagementService;

    @Nested
    @DisplayName("calcularPosicionEnCola()")
    class CalcularPosicion {

        @Test
        @DisplayName("cola vacía → debe retornar posición 1")
        void calcularPosicion_colaVacia_debeRetornarUno() {
            // Given
            when(ticketRepository.countWaitingBefore(eq(QueueType.CAJA), any()))
                .thenReturn(0);

            // When
            int posicion = queueManagementService.calcularPosicionEnCola(QueueType.CAJA);

            // Then
            assertThat(posicion).isEqualTo(1);
        }

        @Test
        @DisplayName("con 5 tickets esperando → debe retornar posición 6")
        void calcularPosicion_con5Esperando_debeRetornarSeis() {
            // Given
            when(ticketRepository.countWaitingBefore(eq(QueueType.CAJA), any()))
                .thenReturn(5);

            // When
            int posicion = queueManagementService.calcularPosicionEnCola(QueueType.CAJA);

            // Then
            assertThat(posicion).isEqualTo(6);
        }
    }

    @Nested
    @DisplayName("calcularTiempoEstimado()")
    class CalcularTiempoEstimado {

        @Test
        @DisplayName("posición 1 → debe retornar 0 minutos")
        void calcularTiempo_posicionUno_debeRetornarCero() {
            // Given
            QueueConfig config = queueConfigCaja().avgServiceTimeMinutes(10).build();
            when(queueConfigRepository.findByQueueType(QueueType.CAJA))
                .thenReturn(Optional.of(config));

            // When
            int tiempo = queueManagementService.calcularTiempoEstimado(QueueType.CAJA, 1);

            // Then
            assertThat(tiempo).isEqualTo(0);
        }

        @Test
        @DisplayName("posición 5 con avgTime=10 → debe retornar 40 minutos")
        void calcularTiempo_posicionCinco_debeCalcularCorrectamente() {
            // Given
            QueueConfig config = queueConfigCaja().avgServiceTimeMinutes(10).build();
            when(queueConfigRepository.findByQueueType(QueueType.CAJA))
                .thenReturn(Optional.of(config));

            // When
            int tiempo = queueManagementService.calcularTiempoEstimado(QueueType.CAJA, 5);

            // Then
            // (5 - 1) × 10 = 40
            assertThat(tiempo).isEqualTo(40);
        }

        @Test
        @DisplayName("config inexistente → debe lanzar RuntimeException")
        void calcularTiempo_sinConfig_debeLanzarExcepcion() {
            // Given
            when(queueConfigRepository.findByQueueType(QueueType.GERENCIA))
                .thenReturn(Optional.empty());

            // When + Then
            assertThatThrownBy(() -> 
                queueManagementService.calcularTiempoEstimado(QueueType.GERENCIA, 1))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("GERENCIA");
        }
    }

    @Nested
    @DisplayName("obtenerSiguienteTicket()")
    class ObtenerSiguienteTicket {

        @Test
        @DisplayName("con tickets en espera → debe retornar el primero")
        void obtenerSiguiente_conTickets_debeRetornarPrimero() {
            // Given
            Ticket ticket1 = ticketWaiting().id(1L).positionInQueue(1).build();
            Ticket ticket2 = ticketWaiting().id(2L).positionInQueue(2).build();
            
            when(ticketRepository.findByQueueAndStatus(QueueType.CAJA, TicketStatus.WAITING))
                .thenReturn(List.of(ticket1, ticket2));

            // When
            Optional<Ticket> siguiente = queueManagementService.obtenerSiguienteTicket(QueueType.CAJA);

            // Then
            assertThat(siguiente).isPresent();
            assertThat(siguiente.get()).isEqualTo(ticket1);
        }

        @Test
        @DisplayName("cola vacía → debe retornar Optional.empty()")
        void obtenerSiguiente_colaVacia_debeRetornarEmpty() {
            // Given
            when(ticketRepository.findByQueueAndStatus(QueueType.CAJA, TicketStatus.WAITING))
                .thenReturn(Collections.emptyList());

            // When
            Optional<Ticket> siguiente = queueManagementService.obtenerSiguienteTicket(QueueType.CAJA);

            // Then
            assertThat(siguiente).isEmpty();
        }
    }

    @Nested
    @DisplayName("obtenerEstadisticas()")
    class ObtenerEstadisticas {

        @Test
        @DisplayName("debe calcular estadísticas correctamente")
        void obtenerEstadisticas_debeCalcularCorrectamente() {
            // Given
            QueueConfig config = queueConfigCaja().avgServiceTimeMinutes(5).build();
            when(queueConfigRepository.findByQueueType(QueueType.CAJA))
                .thenReturn(Optional.of(config));
            when(ticketRepository.countByQueueTypeAndStatus(QueueType.CAJA, TicketStatus.WAITING))
                .thenReturn(10L);
            when(ticketRepository.countByQueueTypeAndStatus(QueueType.CAJA, TicketStatus.CALLED))
                .thenReturn(2L);
            when(ticketRepository.countByQueueTypeAndStatus(QueueType.CAJA, TicketStatus.IN_PROGRESS))
                .thenReturn(1L);
            when(ticketRepository.countByQueueTypeAndStatus(QueueType.CAJA, TicketStatus.COMPLETED))
                .thenReturn(50L);

            // When
            var stats = queueManagementService.obtenerEstadisticas(QueueType.CAJA);

            // Then
            assertThat(stats.waiting()).isEqualTo(10);
            assertThat(stats.called()).isEqualTo(2);
            assertThat(stats.inProgress()).isEqualTo(1);
            assertThat(stats.completed()).isEqualTo(50);
            assertThat(stats.estimatedWaitTime()).isEqualTo(50); // 10 × 5
        }
    }
}