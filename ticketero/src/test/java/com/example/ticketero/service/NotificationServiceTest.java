package com.example.ticketero.service;

import com.example.ticketero.model.entity.*;
import com.example.ticketero.model.enums.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.example.ticketero.testutil.TestDataBuilder.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService - Unit Tests")
class NotificationServiceTest {

    @Mock
    private TelegramService telegramService;

    @InjectMocks
    private NotificationService notificationService;

    @Nested
    @DisplayName("notificarTicketCreado()")
    class NotificarTicketCreado {

        @Test
        @DisplayName("con teléfono → debe enviar notificación")
        void notificar_conTelefono_debeEnviar() {
            // Given
            Ticket ticket = ticketWaiting()
                .telefono("+56912345678")
                .numero("C001")
                .positionInQueue(3)
                .build();

            // When
            notificationService.notificarTicketCreado(ticket);

            // Then
            verify(telegramService).enviarMensaje(
                eq("+56912345678"),
                contains("C001")
            );
        }

        @Test
        @DisplayName("sin teléfono → no debe enviar")
        void notificar_sinTelefono_noDebeEnviar() {
            // Given
            Ticket ticket = ticketWaiting().telefono(null).build();

            // When
            notificationService.notificarTicketCreado(ticket);

            // Then
            verify(telegramService, never()).enviarMensaje(any(), any());
        }

        @Test
        @DisplayName("teléfono vacío → no debe enviar")
        void notificar_telefonoVacio_noDebeEnviar() {
            // Given
            Ticket ticket = ticketWaiting().telefono("   ").build();

            // When
            notificationService.notificarTicketCreado(ticket);

            // Then
            verify(telegramService, never()).enviarMensaje(any(), any());
        }
    }

    @Nested
    @DisplayName("notificarTurnoActivo()")
    class NotificarTurnoActivo {

        @Test
        @DisplayName("con advisor → debe incluir nombre y módulo")
        void notificarTurno_conAdvisor_debeIncluirInfo() {
            // Given
            Advisor advisor = advisorAvailable()
                .name("María López")
                .moduleNumber(3)
                .build();
            Ticket ticket = ticketWaiting()
                .telefono("+56912345678")
                .numero("C001")
                .assignedModuleNumber(3)
                .build();

            // When
            notificationService.notificarTurnoActivo(ticket, advisor);

            // Then
            verify(telegramService).enviarMensaje(
                eq("+56912345678"),
                argThat(msg -> 
                    msg.contains("C001") && 
                    msg.contains("María López") &&
                    msg.contains("3"))
            );
        }

        @Test
        @DisplayName("Telegram falla → no debe propagar excepción")
        void notificarTurno_telegramFalla_noDebePropagar() {
            // Given
            Ticket ticket = ticketWaiting().telefono("+56912345678").build();
            Advisor advisor = advisorAvailable().build();
            doThrow(new RuntimeException("Telegram error"))
                .when(telegramService).enviarMensaje(any(), any());

            // When + Then (no debe lanzar excepción)
            notificationService.notificarTurnoActivo(ticket, advisor);
        }
    }
}