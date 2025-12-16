package com.example.ticketero.integration;

import com.example.ticketero.config.WireMockConfig;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@DisplayName("Feature: Notificaciones Telegram")
@Import(WireMockConfig.class)
class NotificationIT extends BaseIntegrationTest {

    @Autowired
    private WireMockServer wireMockServer;

    @BeforeEach
    void resetWireMock() {
        WireMockConfig.resetMocks(wireMockServer);
    }

    @Nested
    @DisplayName("Escenarios Happy Path (P0)")
    class HappyPath {

        @Test
        @DisplayName("Notificación #1 - Confirmación al crear ticket")
        void crearTicket_debeEnviarNotificacion() {
            // Given
            wireMockServer.resetRequests();

            // When
            given()
                .contentType("application/json")
                .body(createTicketRequest("88888888", "+56912345678", "Sucursal Centro", "CAJA"))
            .when()
                .post("/tickets")
            .then()
                .statusCode(201);

            // Then - Esperar y verificar llamada a Telegram
            await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    wireMockServer.verify(
                        postRequestedFor(urlPathMatching("/bot.*/sendMessage"))
                            .withRequestBody(containing("Ticket Creado"))
                    );
                });
        }

        @Test
        @DisplayName("Notificación #3 - Es tu turno (incluye asesor y módulo)")
        void procesarTicket_debeNotificarTurnoActivo() {
            // Given
            wireMockServer.resetRequests();

            // When - Crear ticket y esperar procesamiento
            given()
                .contentType("application/json")
                .body(createTicketRequest("99999999", "+56987654321", "Sucursal Norte", "CAJA"))
            .when()
                .post("/tickets")
            .then()
                .statusCode(201);

            // Then - Esperar notificación de turno activo
            await()
                .atMost(30, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    wireMockServer.verify(
                        postRequestedFor(urlPathMatching("/bot.*/sendMessage"))
                            .withRequestBody(containing("ES TU TURNO"))
                    );
                });
        }

        @Test
        @DisplayName("Notificación #2 - Próximo turno cuando posición ≤ 3")
        void posicionProxima_debeNotificarProximoTurno() {
            // Given - Crear 4 tickets (el 4to tendrá posición 4)
            for (int i = 1; i <= 4; i++) {
                given()
                    .contentType("application/json")
                    .body(createTicketRequest("1111111" + i, "+5691234567" + i, "Centro", "CAJA"))
                .when()
                    .post("/tickets");
            }

            wireMockServer.resetRequests();

            // When - Esperar que se procesen algunos tickets
            await()
                .atMost(60, TimeUnit.SECONDS)
                .until(() -> countTicketsInStatus("COMPLETED") >= 1);

            // Then - Debería haberse enviado notificación de próximo turno
            // cuando el ticket 4 pasó a posición ≤ 3
            await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    wireMockServer.verify(
                        atLeast(1),
                        postRequestedFor(urlPathMatching("/bot.*/sendMessage"))
                            .withRequestBody(containing("próximo"))
                    );
                });
        }
    }

    @Nested
    @DisplayName("Escenarios Edge Case (P1)")
    class EdgeCases {

        @Test
        @DisplayName("Telegram caído → ticket sigue su flujo, notificación falla silenciosamente")
        void telegramCaido_ticketContinua() {
            // Given - Simular fallo de Telegram
            WireMockConfig.simulateTelegramFailure(wireMockServer);

            // When - Crear ticket
            given()
                .contentType("application/json")
                .body(createTicketRequest("10101010", "+56911111111", "Centro", "CAJA"))
            .when()
                .post("/tickets")
            .then()
                .statusCode(201);

            // Then - El ticket debe seguir procesándose normalmente
            await()
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> countTicketsInStatus("COMPLETED") >= 1);

            // Verificar que el ticket se completó a pesar del fallo de Telegram
            int completed = countTicketsInStatus("COMPLETED");
            assertThat(completed).isGreaterThanOrEqualTo(1);
        }
    }
}