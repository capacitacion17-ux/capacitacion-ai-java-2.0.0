package com.example.ticketero.service;

import com.example.ticketero.exception.TicketNotFoundException;
import com.example.ticketero.model.dto.TicketCreateRequest;
import com.example.ticketero.model.dto.TicketResponse;
import com.example.ticketero.model.entity.OutboxMessage;
import com.example.ticketero.model.entity.Ticket;
import com.example.ticketero.model.enums.QueueType;
import com.example.ticketero.model.enums.TicketStatus;
import com.example.ticketero.repository.OutboxMessageRepository;
import com.example.ticketero.repository.TicketRepository;
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

import java.util.Optional;
import java.util.UUID;

import static com.example.ticketero.testutil.TestDataBuilder.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TicketService - Unit Tests")
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private OutboxMessageRepository outboxMessageRepository;

    @Mock
    private QueueManagementService queueManagementService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private MetricsService metricsService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private TicketService ticketService;

    // ============================================================
    // CREAR TICKET
    // ============================================================
    
    @Nested
    @DisplayName("crearTicket()")
    class CrearTicket {

        @Test
        @DisplayName("con datos válidos → debe crear ticket, guardar en outbox y notificar")
        void crearTicket_conDatosValidos_debeCrearTicketOutboxYNotificar() {
            // Given
            TicketCreateRequest request = validTicketRequest();
            Ticket ticketGuardado = ticketWaiting()
                .numero("C001")
                .positionInQueue(3)
                .estimatedWaitMinutes(10)
                .build();

            when(queueManagementService.calcularPosicionEnCola(QueueType.CAJA)).thenReturn(3);
            when(queueManagementService.calcularTiempoEstimado(QueueType.CAJA, 3)).thenReturn(10);
            when(ticketRepository.saveAndFlush(any(Ticket.class))).thenReturn(ticketGuardado);

            // When
            TicketResponse response = ticketService.crearTicket(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.numero()).isEqualTo("C001");
            assertThat(response.positionInQueue()).isEqualTo(3);
            assertThat(response.estimatedWaitMinutes()).isEqualTo(10);
            assertThat(response.status()).isEqualTo(TicketStatus.WAITING);

            // Verificar orden: primero ticket, luego outbox
            var inOrder = inOrder(ticketRepository, outboxMessageRepository, notificationService);
            inOrder.verify(ticketRepository).saveAndFlush(any(Ticket.class));
            inOrder.verify(outboxMessageRepository).save(any(OutboxMessage.class));
            inOrder.verify(notificationService).notificarTicketCreado(any(Ticket.class));

            verify(metricsService).incrementTicketsCreated(QueueType.CAJA);
        }

        @Test
        @DisplayName("debe guardar mensaje en Outbox con datos correctos")
        void crearTicket_debeGuardarOutboxConDatosCorrectos() {
            // Given
            TicketCreateRequest request = validTicketRequest();
            Ticket ticketGuardado = ticketWaiting().id(99L).numero("C099").build();

            when(queueManagementService.calcularPosicionEnCola(any())).thenReturn(1);
            when(queueManagementService.calcularTiempoEstimado(any(), anyInt())).thenReturn(5);
            when(ticketRepository.saveAndFlush(any())).thenReturn(ticketGuardado);

            // When
            ticketService.crearTicket(request);

            // Then
            ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
            verify(outboxMessageRepository).save(captor.capture());

            OutboxMessage outbox = captor.getValue();
            assertThat(outbox.getAggregateType()).isEqualTo("TICKET");
            assertThat(outbox.getAggregateId()).isEqualTo(99L);
            assertThat(outbox.getEventType()).isEqualTo("TICKET_CREATED");
            assertThat(outbox.getRoutingKey()).isEqualTo("caja-queue");
            assertThat(outbox.getStatus()).isEqualTo("PENDING");
            assertThat(outbox.getPayload()).contains("C099");
        }

        @Test
        @DisplayName("para cola PERSONAL → debe usar routing key personal-queue")
        void crearTicket_colaPersonal_debeUsarRoutingKeyCorrecto() {
            // Given
            TicketCreateRequest request = new TicketCreateRequest(
                "12345678", "+56912345678", "Sucursal Centro", QueueType.PERSONAL
            );
            Ticket ticketGuardado = ticketWaiting()
                .queueType(QueueType.PERSONAL)
                .numero("P001")
                .build();

            when(queueManagementService.calcularPosicionEnCola(any())).thenReturn(1);
            when(queueManagementService.calcularTiempoEstimado(any(), anyInt())).thenReturn(10);
            when(ticketRepository.saveAndFlush(any())).thenReturn(ticketGuardado);

            // When
            ticketService.crearTicket(request);

            // Then
            ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
            verify(outboxMessageRepository).save(captor.capture());
            assertThat(captor.getValue().getRoutingKey()).isEqualTo("personal-queue");
        }

        @Test
        @DisplayName("sin teléfono → debe crear ticket y notificar igual")
        void crearTicket_sinTelefono_debeCrearYNotificar() {
            // Given
            TicketCreateRequest request = ticketRequestSinTelefono();
            Ticket ticketGuardado = ticketWaiting().telefono(null).build();

            when(queueManagementService.calcularPosicionEnCola(any())).thenReturn(1);
            when(queueManagementService.calcularTiempoEstimado(any(), anyInt())).thenReturn(5);
            when(ticketRepository.saveAndFlush(any())).thenReturn(ticketGuardado);

            // When
            TicketResponse response = ticketService.crearTicket(request);

            // Then
            assertThat(response).isNotNull();
            verify(notificationService).notificarTicketCreado(any());
        }
    }

    // ============================================================
    // OBTENER TICKET
    // ============================================================
    
    @Nested
    @DisplayName("obtenerTicketPorCodigo()")
    class ObtenerTicket {

        @Test
        @DisplayName("con UUID existente → debe retornar ticket")
        void obtenerTicket_conUuidExistente_debeRetornarTicket() {
            // Given
            UUID codigo = UUID.randomUUID();
            Ticket ticket = ticketWaiting()
                .codigoReferencia(codigo)
                .numero("C001")
                .build();

            when(ticketRepository.findByCodigoReferencia(codigo)).thenReturn(Optional.of(ticket));

            // When
            TicketResponse response = ticketService.obtenerTicketPorCodigo(codigo);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.codigoReferencia()).isEqualTo(codigo);
            assertThat(response.numero()).isEqualTo("C001");
        }

        @Test
        @DisplayName("con UUID inexistente → debe lanzar TicketNotFoundException")
        void obtenerTicket_conUuidInexistente_debeLanzarExcepcion() {
            // Given
            UUID codigo = UUID.randomUUID();
            when(ticketRepository.findByCodigoReferencia(codigo)).thenReturn(Optional.empty());

            // When + Then
            assertThatThrownBy(() -> ticketService.obtenerTicketPorCodigo(codigo))
                .isInstanceOf(TicketNotFoundException.class)
                .hasMessageContaining(codigo.toString());
        }
    }
}