package com.example.ticketero.integration;

import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

@DisplayName("Feature: Creación de Tickets")
class TicketCreationIT extends BaseIntegrationTest {

    @Nested
    @DisplayName("Escenarios Happy Path (P0)")
    class HappyPath {

        @Test
        @DisplayName("Crear ticket con datos válidos → 201 + status WAITING + Outbox")
        void crearTicket_datosValidos_debeCrearConOutbox() {
            // Given - Sistema operativo con asesores disponibles
            assertThat(countAdvisorsInStatus("AVAILABLE")).isGreaterThan(0);

            // When
            Response response = given()
                .contentType("application/json")
                .body(createTicketRequest("12345678", "CAJA"))
            .when()
                .post("/tickets")
            .then()
                .statusCode(201)
                .body("numero", notNullValue())
                .body("status", equalTo("WAITING"))
                .body("queueType", equalTo("CAJA"))
                .body("positionInQueue", greaterThan(0))
                .body("estimatedWaitMinutes", greaterThanOrEqualTo(0))
                .body("codigoReferencia", notNullValue())
                .extract().response();

            // Then - Verificar BD
            String numero = response.jsonPath().getString("numero");
            assertThat(countTicketsInStatus("WAITING")).isGreaterThanOrEqualTo(1);

            // Verificar Outbox
            int outboxCount = countOutboxMessages("PENDING");
            assertThat(outboxCount).isGreaterThanOrEqualTo(1);

            // Verificar routing key
            String routingKey = jdbcTemplate.queryForObject(
                "SELECT routing_key FROM outbox_message WHERE aggregate_type = 'TICKET' ORDER BY id DESC LIMIT 1",
                String.class);
            assertThat(routingKey).isEqualTo("caja-queue");
        }

        @Test
        @DisplayName("Calcular posición correcta con tickets existentes")
        void crearTicket_conTicketsExistentes_debePosicionCorrecta() {
            // Given - Crear 3 tickets previos
            for (int i = 1; i <= 3; i++) {
                given()
                    .contentType("application/json")
                    .body(createTicketRequest("1000000" + i, "CAJA"))
                .when()
                    .post("/tickets")
                .then()
                    .statusCode(201);
            }

            // When - Crear ticket #4
            Response response = given()
                .contentType("application/json")
                .body(createTicketRequest("10000004", "CAJA"))
            .when()
                .post("/tickets")
            .then()
                .statusCode(201)
                .extract().response();

            // Then
            int posicion = response.jsonPath().getInt("positionInQueue");
            int tiempoEstimado = response.jsonPath().getInt("estimatedWaitMinutes");

            assertThat(posicion).isEqualTo(4);
            // Tiempo = (posición - 1) × avgTime(5) = 3 × 5 = 15
            assertThat(tiempoEstimado).isEqualTo(15);
        }

        @Test
        @DisplayName("Crear ticket sin teléfono → debe funcionar")
        void crearTicket_sinTelefono_debeCrear() {
            // Given
            String requestSinTelefono = """
                {
                    "nationalId": "87654321",
                    "branchOffice": "Sucursal Norte",
                    "queueType": "PERSONAL"
                }
                """;

            // When + Then
            given()
                .contentType("application/json")
                .body(requestSinTelefono)
            .when()
                .post("/tickets")
            .then()
                .statusCode(201)
                .body("numero", startsWith("P"));
        }

        @Test
        @DisplayName("Crear tickets para diferentes colas → posiciones independientes")
        void crearTickets_diferentesColas_posicionesIndependientes() {
            // Given + When - Crear un ticket por cada cola
            String[] colas = {"CAJA", "PERSONAL", "EMPRESAS", "GERENCIA"};
            String[] prefijos = {"C", "P", "E", "G"};

            for (int i = 0; i < colas.length; i++) {
                Response response = given()
                    .contentType("application/json")
                    .body(createTicketRequest("2000000" + i, colas[i]))
                .when()
                    .post("/tickets")
                .then()
                    .statusCode(201)
                    .extract().response();

                // Then - Cada cola empieza en posición 1
                assertThat(response.jsonPath().getInt("positionInQueue")).isEqualTo(1);
                assertThat(response.jsonPath().getString("numero")).startsWith(prefijos[i]);
            }

            // Verificar 4 mensajes en Outbox
            assertThat(countOutboxMessages("PENDING")).isGreaterThanOrEqualTo(4);
        }
    }

    @Nested
    @DisplayName("Escenarios Edge Case (P1)")
    class EdgeCases {

        @Test
        @DisplayName("Número de ticket tiene formato correcto")
        void crearTicket_debeGenerarNumeroConFormato() {
            // When
            Response response = given()
                .contentType("application/json")
                .body(createTicketRequest("11111111", "PERSONAL"))
            .when()
                .post("/tickets")
            .then()
                .statusCode(201)
                .extract().response();

            // Then
            String numero = response.jsonPath().getString("numero");
            assertThat(numero).matches("P\\d{3}"); // P seguido de 3 dígitos
        }

        @Test
        @DisplayName("Consultar ticket por código de referencia")
        void consultarTicket_porCodigo_debeRetornarDatos() {
            // Given - Crear ticket
            Response createResponse = given()
                .contentType("application/json")
                .body(createTicketRequest("22222222", "CAJA"))
            .when()
                .post("/tickets")
            .then()
                .statusCode(201)
                .extract().response();

            String codigoReferencia = createResponse.jsonPath().getString("codigoReferencia");

            // When + Then
            given()
            .when()
                .get("/tickets/" + codigoReferencia)
            .then()
                .statusCode(200)
                .body("codigoReferencia", equalTo(codigoReferencia))
                .body("status", equalTo("WAITING"))
                .body("positionInQueue", notNullValue())
                .body("estimatedWaitMinutes", notNullValue());
        }
    }
}