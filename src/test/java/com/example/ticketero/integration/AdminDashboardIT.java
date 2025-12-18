package com.example.ticketero.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@DisplayName("Feature: Dashboard Administrativo")
class AdminDashboardIT extends BaseIntegrationTest {

    @Nested
    @DisplayName("Dashboard General")
    class DashboardGeneral {

        @Test
        @DisplayName("GET /api/admin/dashboard → estado del sistema")
        void dashboard_debeRetornarEstado() {
            // Given - Crear algunos tickets
            given()
                .contentType("application/json")
                .body(createTicketRequest("20000001", "CAJA"))
            .when()
                .post("/tickets");

            given()
                .contentType("application/json")
                .body(createTicketRequest("20000002", "PERSONAL"))
            .when()
                .post("/tickets");

            // When + Then
            given()
            .when()
                .get("/admin/dashboard")
            .then()
                .statusCode(200)
                .body("ticketsPorCola", notNullValue())
                .body("estadisticasAsesores", notNullValue())
                .body("timestamp", notNullValue());
        }
    }

    @Nested
    @DisplayName("Estado de Colas")
    class EstadoColas {

        @Test
        @DisplayName("GET /api/admin/queues/CAJA → tickets de la cola")
        void colaEspecifica_debeRetornarTickets() {
            // Given - Crear tickets en cola CAJA
            for (int i = 1; i <= 3; i++) {
                given()
                    .contentType("application/json")
                    .body(createTicketRequest("3000000" + i, "CAJA"))
                .when()
                    .post("/tickets");
            }

            // When + Then
            given()
            .when()
                .get("/admin/queues/CAJA")
            .then()
                .statusCode(200)
                .body("queueType", equalTo("CAJA"))
                .body("totalActivos", greaterThanOrEqualTo(0));
        }

        @Test
        @DisplayName("GET /api/admin/queues/CAJA/stats → estadísticas")
        void estadisticasCola_debeRetornarMetricas() {
            given()
            .when()
                .get("/admin/queues/CAJA/stats")
            .then()
                .statusCode(200)
                .body("queueType", equalTo("CAJA"))
                .body("waiting", greaterThanOrEqualTo(0))
                .body("completed", greaterThanOrEqualTo(0))
                .body("avgServiceTimeMinutes", greaterThan(0));
        }
    }

    @Nested
    @DisplayName("Gestión de Asesores")
    class GestionAsesores {

        @Test
        @DisplayName("PUT /api/admin/advisors/{id}/status → cambiar estado")
        void cambiarEstado_debeActualizar() {
            // Given - Verificar que existe asesor 1
            Long advisorId = jdbcTemplate.queryForObject(
                "SELECT id FROM advisor LIMIT 1", Long.class);

            // When
            given()
                .queryParam("status", "BREAK")
            .when()
                .put("/admin/advisors/" + advisorId + "/status")
            .then()
                .statusCode(200)
                .body(containsString("BREAK"));

            // Then - Verificar en BD
            String status = jdbcTemplate.queryForObject(
                "SELECT status FROM advisor WHERE id = ?",
                String.class, advisorId);
            org.assertj.core.api.Assertions.assertThat(status).isEqualTo("BREAK");

            // Cleanup
            jdbcTemplate.update(
                "UPDATE advisor SET status = 'AVAILABLE' WHERE id = ?", advisorId);
        }

        @Test
        @DisplayName("GET /api/admin/advisors/stats → estadísticas")
        void estadisticasAsesores_debeRetornarMetricas() {
            given()
            .when()
                .get("/admin/advisors/stats")
            .then()
                .statusCode(200)
                .body("total", greaterThan(0))
                .body("disponibles", greaterThanOrEqualTo(0))
                .body("ocupados", greaterThanOrEqualTo(0))
                .body("totalTicketsAtendidos", greaterThanOrEqualTo(0));
        }
    }
}