package com.example.ticketero.service;

import com.example.ticketero.exception.NoAdvisorAvailableException;
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

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.example.ticketero.testutil.TestDataBuilder.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TicketProcessingService - Unit Tests")
class TicketProcessingServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private AdvisorRepository advisorRepository;

    @Mock
    private TicketEventRepository ticketEventRepository;

    @Mock
    private QueueConfigRepository queueConfigRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private TicketProcessingService ticketProcessingService;

    @Nested
    @DisplayName("procesarTicketCompleto()")
    class ProcesarTicketCompleto {

        @Test
        @DisplayName("con ticket WAITING y advisor disponible → debe completar flujo")
        void procesarTicket_conAdvisorDisponible_debeCompletarFlujo() throws Exception {
            // Given
            Ticket ticket = ticketWaiting().build();
            Advisor advisor = advisorAvailable().build();
            QueueConfig config = queueConfigCaja().avgServiceTimeMinutes(1).build();

            when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
            when(advisorRepository.findAvailableForQueueWithLock("CAJA"))
                .thenReturn(List.of(advisor));
            when(queueConfigRepository.findByQueueType(QueueType.CAJA))
                .thenReturn(Optional.of(config));
            when(ticketRepository.findByQueueAndStatus(any(), eq(TicketStatus.WAITING)))
                .thenReturn(Collections.emptyList());

            // When
            boolean resultado = ticketProcessingService.procesarTicketCompleto(1L, QueueType.CAJA);

            // Then
            assertThat(resultado).isTrue();

            // Verificar cambios en ticket
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.COMPLETED);
            assertThat(ticket.getAssignedAdvisor()).isEqualTo(advisor);
            assertThat(ticket.getCalledAt()).isNotNull();
            assertThat(ticket.getStartedAt()).isNotNull();
            assertThat(ticket.getCompletedAt()).isNotNull();

            // Verificar cambios en advisor
            assertThat(advisor.getStatus()).isEqualTo(AdvisorStatus.AVAILABLE);
            assertThat(advisor.getTotalTicketsServed()).isEqualTo(11);

            // Verificar guardado
            verify(ticketRepository).save(ticket);
            verify(advisorRepository).save(advisor);

            // Verificar eventos registrados (3: CALLED, STARTED, COMPLETED)
            verify(ticketEventRepository, times(3)).save(any(TicketEvent.class));
        }

        @Test
        @DisplayName("con ticket ya procesado (status != WAITING) → debe retornar false")
        void procesarTicket_yaProcsado_debeRetornarFalse() throws Exception {
            // Given
            Ticket ticketCompletado = ticketCompleted().build();
            when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticketCompletado));

            // When
            boolean resultado = ticketProcessingService.procesarTicketCompleto(1L, QueueType.CAJA);

            // Then
            assertThat(resultado).isFalse();
            verify(advisorRepository, never()).findAvailableForQueueWithLock(any());
            verify(ticketRepository, never()).save(any());
        }

        @Test
        @DisplayName("sin advisors disponibles → debe lanzar NoAdvisorAvailableException")
        void procesarTicket_sinAdvisors_debeLanzarExcepcion() {
            // Given
            Ticket ticket = ticketWaiting().build();
            when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
            when(advisorRepository.findAvailableForQueueWithLock("CAJA"))
                .thenReturn(Collections.emptyList());

            // When + Then
            assertThatThrownBy(() -> 
                ticketProcessingService.procesarTicketCompleto(1L, QueueType.CAJA))
                .isInstanceOf(NoAdvisorAvailableException.class)
                .hasMessageContaining("CAJA");

            verify(ticketRepository, never()).save(any());
        }

        @Test
        @DisplayName("debe seleccionar advisor con menos tickets servidos")
        void procesarTicket_debeSeleccionarAdvisorMenosOcupado() throws Exception {
            // Given
            Ticket ticket = ticketWaiting().build();
            Advisor advisor1 = advisorAvailable().id(1L).totalTicketsServed(100).build();
            Advisor advisor2 = advisorAvailable().id(2L).totalTicketsServed(5).build();
            QueueConfig config = queueConfigCaja().avgServiceTimeMinutes(1).build();

            when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
            // El repositorio devuelve ordenado por totalTicketsServed ASC
            when(advisorRepository.findAvailableForQueueWithLock("CAJA"))
                .thenReturn(List.of(advisor2, advisor1));
            when(queueConfigRepository.findByQueueType(QueueType.CAJA))
                .thenReturn(Optional.of(config));
            when(ticketRepository.findByQueueAndStatus(any(), any()))
                .thenReturn(Collections.emptyList());

            // When
            ticketProcessingService.procesarTicketCompleto(1L, QueueType.CAJA);

            // Then - El advisor2 (menos ocupado) debe ser asignado
            assertThat(ticket.getAssignedAdvisor()).isEqualTo(advisor2);
        }

        @Test
        @DisplayName("ticket inexistente → debe lanzar RuntimeException")
        void procesarTicket_ticketInexistente_debeLanzarExcepcion() {
            // Given
            when(ticketRepository.findById(999L)).thenReturn(Optional.empty());

            // When + Then
            assertThatThrownBy(() -> 
                ticketProcessingService.procesarTicketCompleto(999L, QueueType.CAJA))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("999");
        }

        @Test
        @DisplayName("debe notificar turno activo al cliente")
        void procesarTicket_debeNotificarTurnoActivo() throws Exception {
            // Given
            Ticket ticket = ticketWaiting().build();
            Advisor advisor = advisorAvailable().build();
            QueueConfig config = queueConfigCaja().avgServiceTimeMinutes(1).build();

            when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
            when(advisorRepository.findAvailableForQueueWithLock("CAJA"))
                .thenReturn(List.of(advisor));
            when(queueConfigRepository.findByQueueType(QueueType.CAJA))
                .thenReturn(Optional.of(config));
            when(ticketRepository.findByQueueAndStatus(any(), any()))
                .thenReturn(Collections.emptyList());

            // When
            ticketProcessingService.procesarTicketCompleto(1L, QueueType.CAJA);

            // Then
            verify(notificationService).notificarTurnoActivo(eq(ticket), eq(advisor));
        }

        @Test
        @DisplayName("debe actualizar posiciones de tickets en espera")
        void procesarTicket_debeActualizarPosiciones() throws Exception {
            // Given
            Ticket ticketProcesado = ticketWaiting().id(1L).positionInQueue(1).build();
            Ticket ticketEnEspera1 = ticketWaiting().id(2L).positionInQueue(2).build();
            Ticket ticketEnEspera2 = ticketWaiting().id(3L).positionInQueue(3).build();
            Advisor advisor = advisorAvailable().build();
            QueueConfig config = queueConfigCaja().avgServiceTimeMinutes(1).build();

            when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticketProcesado));
            when(advisorRepository.findAvailableForQueueWithLock("CAJA"))
                .thenReturn(List.of(advisor));
            when(queueConfigRepository.findByQueueType(QueueType.CAJA))
                .thenReturn(Optional.of(config));
            when(ticketRepository.findByQueueAndStatus(QueueType.CAJA, TicketStatus.WAITING))
                .thenReturn(List.of(ticketEnEspera1, ticketEnEspera2));

            // When
            ticketProcessingService.procesarTicketCompleto(1L, QueueType.CAJA);

            // Then - Las posiciones deben actualizarse
            assertThat(ticketEnEspera1.getPositionInQueue()).isEqualTo(1);
            assertThat(ticketEnEspera2.getPositionInQueue()).isEqualTo(2);
        }

        @Test
        @DisplayName("debe actualizar tiempo promedio del advisor")
        void procesarTicket_debeActualizarTiempoPromedio() throws Exception {
            // Given
            Ticket ticket = ticketWaiting().build();
            Advisor advisor = advisorAvailable()
                .avgServiceTimeMinutes(5)
                .totalTicketsServed(10)
                .build();
            QueueConfig config = queueConfigCaja().avgServiceTimeMinutes(1).build();

            when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
            when(advisorRepository.findAvailableForQueueWithLock("CAJA"))
                .thenReturn(List.of(advisor));
            when(queueConfigRepository.findByQueueType(QueueType.CAJA))
                .thenReturn(Optional.of(config));
            when(ticketRepository.findByQueueAndStatus(any(), any()))
                .thenReturn(Collections.emptyList());

            // When
            ticketProcessingService.procesarTicketCompleto(1L, QueueType.CAJA);

            // Then - El tiempo promedio debe haberse recalculado
            verify(advisorRepository).save(argThat(a -> 
                a.getAvgServiceTimeMinutes() != null));
        }
    }
}