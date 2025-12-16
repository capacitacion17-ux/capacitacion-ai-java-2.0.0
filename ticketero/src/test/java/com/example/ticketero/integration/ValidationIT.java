package com.example.ticketero.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@DisplayName("Feature: Validaciones de Input")
class ValidationIT extends BaseIntegrationTest {

    @Nested
    @DisplayName("Validación de nationalId")
    class NationalIdValidation {

        @ParameterizedTest(name = "nationalId={0} → HTTP {1}")
        @CsvSource({
            "1234567, 400",      // 7 dígitos - muy corto
            "12345678, 201",     // 8 dígitos - válido (límite inferior)
            "123456789, 201",    // 9 dígitos - válido
            "123456789012, 201", // 12 dígitos - válido (límite superior)
            "1234567890123, 400" // 13 dígitos - muy largo
        })
        @DisplayName("Validar longitud de nationalId")
        void validarLongitud_nationalId(String nationalId, int expectedStatus) {
            given()
                .contentType("application/json")
                .body(createTicketRequest(nationalId, "CAJA"))
            .when()
                .post("/tickets")
            .then()
                .statusCode(expectedStatus);
        }

        @Test
        @DisplayName("nationalId con letras → 400")
        void nationalId_conLetras_debeRechazar() {
            given()
                .contentType("application/json")
                .body(createTicketRequest("12345ABC", "CAJA"))
            .when()
                .post("/tickets")
            .then()
                .statusCode(400)
                .body("message", containsString("nationalId"));
        }

        @Test
        @DisplayName("nationalId vacío → 400")
        void nationalId_vacio_debeRechazar() {
            String request = """
                {
                    "nationalId": "",
                    "branchOffice": "Centro",
                    "queueType": "CAJA"
                }
                """;

            given()
                .contentType("application/json")
                .body(request)
            .when()
                .post("/tickets")
            .then()
                .statusCode(400);
        }
    }

    @Nested
    @DisplayName("Validación de queueType")
    class QueueTypeValidation {

        @Test
        @DisplayName("queueType inválido → 400")
        void queueType_invalido_debeRechazar() {
            String request = """
                {
                    "nationalId": "12345678",
                    "branchOffice": "Centro",
                    "queueType": "INVALIDO"
                }
                """;

            given()
                .contentType("application/json")
                .body(request)
            .when()
                .post("/tickets")
            .then()
                .statusCode(400);
        }

        @Test
        @DisplayName("queueType null → 400")
        void queueType_null_debeRechazar() {
            String request = """
                {
                    "nationalId": "12345678",
                    "branchOffice": "Centro"
                }
                """;

            given()
                .contentType("application/json")
                .body(request)
            .when()
                .post("/tickets")
            .then()
                .statusCode(400);
        }
    }

    @Nested
    @DisplayName("Validación de campos requeridos")
    class RequiredFieldsValidation {

        @Test
        @DisplayName("branchOffice vacío → 400")
        void branchOffice_vacio_debeRechazar() {
            String request = """
                {
                    "nationalId": "12345678",
                    "branchOffice": "",
                    "queueType": "CAJA"
                }
                """;

            given()
                .contentType("application/json")
                .body(request)
            .when()
                .post("/tickets")
            .then()
                .statusCode(400);
        }
    }

    @Nested
    @DisplayName("Recursos no encontrados")
    class NotFoundValidation {

        @Test
        @DisplayName("Ticket inexistente → 404")
        void ticket_inexistente_debe404() {
            UUID uuidInexistente = UUID.randomUUID();

            given()
            .when()
                .get("/tickets/" + uuidInexistente)
            .then()
                .statusCode(404)
                .body("message", containsString(uuidInexistente.toString()));
        }
    }
}