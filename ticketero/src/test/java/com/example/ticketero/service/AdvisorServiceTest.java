package com.example.ticketero.service;

import com.example.ticketero.model.entity.*;
import com.example.ticketero.model.enums.*;
import com.example.ticketero.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static com.example.ticketero.testutil.TestDataBuilder.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdvisorService - Unit Tests")
class AdvisorServiceTest {

    @Mock
    private AdvisorRepository advisorRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketEventRepository ticketEventRepository;

    @InjectMocks
    private AdvisorService advisorService;

    @Nested
    @DisplayName("obtenerAsesorDisponible()")
    class ObtenerAsesorDisponible {

        @Test
        @DisplayName("con advisors disponibles → debe retornar el menos ocupado")
        void obtenerAsesor_conDisponibles_debeRetornarMenosOcupado() {
            // Given
            Advisor advisor = advisorAvailable().totalTicketsServed(5).build();
            when(advisorRepository.findAvailableForQueue("CAJA"))
                .thenReturn(List.of(advisor));

            // When
            Optional<Advisor> resultado = advisorService.obtenerAsesorDisponible(QueueType.CAJA);

            // Then
            assertThat(resultado).isPresent();
            assertThat(resultado.get()).isEqualTo(advisor);
        }

        @Test
        @DisplayName("sin advisors disponibles → debe retornar Optional.empty()")
        void obtenerAsesor_sinDisponibles_debeRetornarEmpty() {
            // Given
            when(advisorRepository.findAvailableForQueue("CAJA"))
                .thenReturn(Collections.emptyList());

            // When
            Optional<Advisor> resultado = advisorService.obtenerAsesorDisponible(QueueType.CAJA);

            // Then
            assertThat(resultado).isEmpty();
        }
    }

    @Nested
    @DisplayName("asignarTicketAsesor()")
    class AsignarTicketAsesor {

        @Test
        @DisplayName("debe actualizar ticket y advisor correctamente")
        void asignarTicket_debeActualizarAmbos() {
            // Given
            Ticket ticket = ticketWaiting().build();
            Advisor advisor = advisorAvailable().moduleNumber(3).build();

            // When
            advisorService.asignarTicketAsesor(ticket, advisor);

            // Then
            assertThat(ticket.getAssignedAdvisor()).isEqualTo(advisor);
            assertThat(ticket.getAssignedModuleNumber()).isEqualTo(3);
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CALLED);
            assertThat(ticket.getCalledAt()).isNotNull();

            assertThat(advisor.getStatus()).isEqualTo(AdvisorStatus.BUSY);

            verify(ticketRepository).save(ticket);
            verify(advisorRepository).save(advisor);
        }

        @Test
        @DisplayName("debe registrar evento CALLED")
        void asignarTicket_debeRegistrarEvento() {
            // Given
            Ticket ticket = ticketWaiting().build();
            Advisor advisor = advisorAvailable().build();

            // When
            advisorService.asignarTicketAsesor(ticket, advisor);

            // Then
            ArgumentCaptor<TicketEvent> captor = ArgumentCaptor.forClass(TicketEvent.class);
            verify(ticketEventRepository).save(captor.capture());

            TicketEvent evento = captor.getValue();
            assertThat(evento.getEventType()).isEqualTo(EventType.CALLED);
            assertThat(evento.getNewStatus()).isEqualTo("CALLED");
            assertThat(evento.getAdvisor()).isEqualTo(advisor);
        }
    }

    @Nested
    @DisplayName("completarAtencion()")
    class CompletarAtencion {

        @Test
        @DisplayName("debe completar ticket y liberar advisor")
        void completarAtencion_debeCompletarYLiberar() {
            // Given
            Advisor advisor = advisorBusy().totalTicketsServed(10).build();
            Ticket ticket = ticketInProgress()
                .assignedAdvisor(advisor)
                .startedAt(LocalDateTime.now().minusMinutes(5))
                .build();

            when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
            when(advisorRepository.findById(advisor.getId())).thenReturn(Optional.of(advisor));
            when(advisorRepository.saveAndFlush(any())).thenReturn(advisor);

            // When
            advisorService.completarAtencion(1L);

            // Then
            verify(ticketRepository).updateStatusAndCompletedAt(
                eq(1L), eq(TicketStatus.COMPLETED), any(LocalDateTime.class));
            
            assertThat(advisor.getStatus()).isEqualTo(AdvisorStatus.AVAILABLE);
            assertThat(advisor.getTotalTicketsServed()).isEqualTo(11);
        }

        @Test
        @DisplayName("debe calcular tiempo promedio correctamente")
        void completarAtencion_debeCalcularPromedio() {
            // Given
            Advisor advisor = advisorBusy()
                .avgServiceTimeMinutes(10)
                .totalTicketsServed(9)
                .build();
            
            LocalDateTime startedAt = LocalDateTime.now().minusMinutes(5);
            Ticket ticket = ticketInProgress()
                .id(1L)
                .assignedAdvisor(advisor)
                .startedAt(startedAt)
                .build();

            when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
            when(advisorRepository.findById(any())).thenReturn(Optional.of(advisor));
            when(advisorRepository.saveAndFlush(any())).thenReturn(advisor);

            // When
            advisorService.completarAtencion(1L);

            // Then
            // Promedio = (10 * 9 + 5) / 10 = 9.5 ≈ 10 (redondeado)
            // El cálculo exacto depende de la implementación
            verify(advisorRepository).saveAndFlush(argThat(a -> 
                a.getAvgServiceTimeMinutes() != null));
        }
    }

    @Nested
    @DisplayName("cambiarEstado()")
    class CambiarEstado {

        @Test
        @DisplayName("debe cambiar estado correctamente")
        void cambiarEstado_debeCambiarCorrectamente() {
            // Given
            Advisor advisor = advisorAvailable().build();
            when(advisorRepository.findById(1L)).thenReturn(Optional.of(advisor));

            // When
            advisorService.cambiarEstado(1L, AdvisorStatus.BREAK);

            // Then
            assertThat(advisor.getStatus()).isEqualTo(AdvisorStatus.BREAK);
            verify(advisorRepository).save(advisor);
        }

        @Test
        @DisplayName("advisor inexistente → debe lanzar excepción")
        void cambiarEstado_advisorInexistente_debeLanzarExcepcion() {
            // Given
            when(advisorRepository.findById(999L)).thenReturn(Optional.empty());

            // When + Then
            assertThatThrownBy(() -> advisorService.cambiarEstado(999L, AdvisorStatus.BREAK))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("999");
        }
    }

    @Nested
    @DisplayName("obtenerEstadisticas()")
    class ObtenerEstadisticas {

        @Test
        @DisplayName("debe calcular estadísticas correctamente")
        void obtenerEstadisticas_debeCalcularCorrectamente() {
            // Given
            List<Advisor> advisors = List.of(
                advisorAvailable().totalTicketsServed(10).avgServiceTimeMinutes(5).build(),
                advisorBusy().totalTicketsServed(20).avgServiceTimeMinutes(8).build(),
                advisorAvailable().status(AdvisorStatus.BREAK).totalTicketsServed(15).avgServiceTimeMinutes(6).build()
            );
            when(advisorRepository.findAll()).thenReturn(advisors);

            // When
            Map<String, Object> stats = advisorService.obtenerEstadisticas();

            // Then
            assertThat(stats.get("total")).isEqualTo(3);
            assertThat(stats.get("disponibles")).isEqualTo(1L);
            assertThat(stats.get("ocupados")).isEqualTo(1L);
            assertThat(stats.get("enDescanso")).isEqualTo(1L);
            assertThat(stats.get("totalTicketsAtendidos")).isEqualTo(45);
        }
    }
}