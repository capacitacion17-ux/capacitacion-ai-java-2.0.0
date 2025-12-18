package com.example.ticketero.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@DisplayName("Feature: Procesamiento de Tickets")
class TicketProcessingIT extends BaseIntegrationTest {

    @Nested
    @DisplayName("Escenarios Happy Path (P0)")
    class HappyPath {

        @Test
        @DisplayName("Procesar ticket completo → WAITING → COMPLETED")
        void procesarTicket_debeCompletarFlujo() {
            // Given - Asesores disponibles
            int asesoresDisponibles = countAdvisorsInStatus("AVAILABLE");
            assertThat(asesoresDisponibles).isGreaterThan(0);

            // When - Crear ticket (worker lo procesará automáticamente)
            given()
                .contentType("application/json")
                .body(createTicketRequest("33333333", "CAJA"))
            .when()
                .post("/tickets")
            .then()
                .statusCode(201);

            // Then - Esperar procesamiento completo
            await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> countTicketsInStatus("COMPLETED") >= 1);

            // Verificar asesor liberado
            assertThat(countAdvisorsInStatus("AVAILABLE")).isEqualTo(asesoresDisponibles);

            // Verificar contador incrementado
            Integer totalServed = jdbcTemplate.queryForObject(
                "SELECT SUM(total_tickets_served) FROM advisor",
                Integer.class);
            assertThat(totalServed).isGreaterThan(0);
        }

        @Test
        @DisplayName("Múltiples tickets se procesan en orden FIFO")
        void procesarTickets_debenSerFIFO() {
            // Given - Crear 3 tickets en orden
            String[] nationalIds = {"44444441", "44444442", "44444443"};
            
            for (String id : nationalIds) {
                given()
                    .contentType("application/json")
                    .body(createTicketRequest(id, "CAJA"))
                .when()
                    .post("/tickets")
                .then()
                    .statusCode(201);
                
                // Pequeña pausa para garantizar orden
                try { Thread.sleep(100); } catch (InterruptedException e) {}
            }

            // When - Esperar que todos se completen
            await()
                .atMost(60, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .until(() -> countTicketsInStatus("COMPLETED") >= 3);

            // Then - Verificar orden por completed_at
            var completedOrder = jdbcTemplate.queryForList(
                "SELECT national_id FROM ticket WHERE status = 'COMPLETED' ORDER BY completed_at ASC",
                String.class);

            // El primero en crearse debería ser el primero en completarse
            assertThat(completedOrder.get(0)).isEqualTo("44444441");
        }
    }

    @Nested
    @DisplayName("Escenarios Edge Case (P1)")
    class EdgeCases {

        @Test
        @DisplayName("Sin asesores disponibles → ticket permanece WAITING")
        void sinAsesores_ticketPermanece() {
            // Given - Poner todos los asesores en BUSY
            jdbcTemplate.execute("UPDATE advisor SET status = 'BUSY'");
            assertThat(countAdvisorsInStatus("AVAILABLE")).isZero();

            // When - Crear ticket
            given()
                .contentType("application/json")
                .body(createTicketRequest("55555555", "CAJA"))
            .when()
                .post("/tickets")
            .then()
                .statusCode(201);

            // Then - Esperar un poco y verificar que sigue WAITING
            try { Thread.sleep(5000); } catch (InterruptedException e) {}
            
            assertThat(countTicketsInStatus("WAITING")).isGreaterThanOrEqualTo(1);
            assertThat(countTicketsInStatus("COMPLETED")).isZero();

            // Cleanup - Restaurar asesores
            jdbcTemplate.execute("UPDATE advisor SET status = 'AVAILABLE'");
        }

        @Test
        @DisplayName("Idempotencia - ticket COMPLETED no se reprocesa")
        void ticketCompletado_noSeReprocesa() {
            // Given - Crear y esperar que se complete
            given()
                .contentType("application/json")
                .body(createTicketRequest("66666666", "CAJA"))
            .when()
                .post("/tickets");

            await()
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> countTicketsInStatus("COMPLETED") >= 1);

            // Guardar estado actual
            int totalServedBefore = jdbcTemplate.queryForObject(
                "SELECT SUM(total_tickets_served) FROM advisor",
                Integer.class);

            // When - Esperar más tiempo (si se reprocesara, cambiaría)
            try { Thread.sleep(5000); } catch (InterruptedException e) {}

            // Then - Nada debe haber cambiado
            int totalServedAfter = jdbcTemplate.queryForObject(
                "SELECT SUM(total_tickets_served) FROM advisor",
                Integer.class);
            
            assertThat(totalServedAfter).isEqualTo(totalServedBefore);
        }

        @Test
        @DisplayName("Asesor en BREAK no recibe tickets")
        void asesorEnBreak_noRecibeTickets() {
            // Given - Poner un asesor en BREAK
            jdbcTemplate.execute("UPDATE advisor SET status = 'BREAK' WHERE id = 1");
            int availableBefore = countAdvisorsInStatus("AVAILABLE");

            // When - Crear ticket
            given()
                .contentType("application/json")
                .body(createTicketRequest("77777777", "CAJA"))
            .when()
                .post("/tickets");

            // Esperar procesamiento
            await()
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> countTicketsInStatus("COMPLETED") >= 1);

            // Then - El asesor en BREAK no debe haber sido asignado
            String breakAdvisorStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM advisor WHERE id = 1",
                String.class);
            assertThat(breakAdvisorStatus).isEqualTo("BREAK");

            // Cleanup
            jdbcTemplate.execute("UPDATE advisor SET status = 'AVAILABLE' WHERE id = 1");
        }
    }
}