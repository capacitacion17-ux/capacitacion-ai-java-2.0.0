package com.example.ticketero.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test para validar que la configuración base de TestContainers funciona correctamente.
 */
@DisplayName("Validación de Setup Base")
class BaseIntegrationTestValidation extends BaseIntegrationTest {

    @Test
    @DisplayName("TestContainers PostgreSQL debe estar funcionando")
    void testContainers_postgresql_debeEstarFuncionando() {
        // Given - TestContainers iniciados
        assertThat(postgres.isRunning()).isTrue();
        
        // When - Ejecutar query simple
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        
        // Then
        assertThat(result).isEqualTo(1);
    }

    @Test
    @DisplayName("TestContainers RabbitMQ debe estar funcionando")
    void testContainers_rabbitmq_debeEstarFuncionando() {
        // Given - TestContainers iniciados
        assertThat(rabbitmq.isRunning()).isTrue();
        
        // When - Verificar puertos expuestos
        Integer amqpPort = rabbitmq.getAmqpPort();
        Integer httpPort = rabbitmq.getHttpPort();
        
        // Then
        assertThat(amqpPort).isGreaterThan(0);
        assertThat(httpPort).isGreaterThan(0);
    }

    @Test
    @DisplayName("Base de datos debe tener tablas inicializadas")
    void baseDatos_debeEstarInicializada() {
        // When - Verificar que existen las tablas principales
        Integer ticketTableExists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'ticket'",
            Integer.class);
        
        Integer advisorTableExists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'advisor'",
            Integer.class);
        
        Integer outboxTableExists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'outbox_message'",
            Integer.class);
        
        // Then
        assertThat(ticketTableExists).isEqualTo(1);
        assertThat(advisorTableExists).isEqualTo(1);
        assertThat(outboxTableExists).isEqualTo(1);
    }

    @Test
    @DisplayName("Utilidades de test deben funcionar correctamente")
    void utilidades_debenFuncionar() {
        // When - Usar utilidades
        String ticketRequest = createTicketRequest("12345678", "CAJA");
        int ticketCount = countTicketsInStatus("WAITING");
        int advisorCount = countAdvisorsInStatus("AVAILABLE");
        
        // Then
        assertThat(ticketRequest).contains("12345678");
        assertThat(ticketRequest).contains("CAJA");
        assertThat(ticketCount).isGreaterThanOrEqualTo(0);
        assertThat(advisorCount).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("RestAssured debe estar configurado correctamente")
    void restAssured_debeEstarConfigurado() {
        // When - Verificar configuración de RestAssured
        int configuredPort = io.restassured.RestAssured.port;
        String basePath = io.restassured.RestAssured.basePath;
        
        // Then
        assertThat(configuredPort).isEqualTo(port);
        assertThat(basePath).isEqualTo("/api");
    }
}