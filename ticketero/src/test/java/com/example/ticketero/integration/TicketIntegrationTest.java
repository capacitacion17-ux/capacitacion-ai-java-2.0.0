package com.example.ticketero.integration;

import com.example.ticketero.model.dto.TicketCreateRequest;
import com.example.ticketero.model.dto.TicketResponse;
import com.example.ticketero.model.enums.QueueType;
import com.example.ticketero.model.enums.TicketStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de integración completo con TestContainers.
 * 
 * Levanta PostgreSQL y RabbitMQ reales en contenedores Docker
 * para validar el flujo end-to-end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class TicketIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ticketero_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-alpine")
            .withExposedPorts(5672);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Configurar datasource con valores de TestContainers
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        
        // Configurar RabbitMQ con valores de TestContainers
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void debeCrearTicketCorrectamente() {
        // Given
        TicketCreateRequest request = new TicketCreateRequest(
                "12345678",
                "987654321",
                "Sucursal Centro",
                QueueType.CAJA
        );

        // When
        ResponseEntity<TicketResponse> response = restTemplate
                .withBasicAuth("test", "test")
                .postForEntity("/api/tickets", request, TicketResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().numero()).isNotBlank();
        assertThat(response.getBody().queueType()).isEqualTo(QueueType.CAJA);
        assertThat(response.getBody().status()).isEqualTo(TicketStatus.WAITING);
        assertThat(response.getBody().positionInQueue()).isGreaterThan(0);
    }

    @Test
    void debeRetornar401SinAutenticacion() {
        // Given
        TicketCreateRequest request = new TicketCreateRequest(
                "12345678",
                "987654321",
                "Sucursal Centro",
                QueueType.CAJA
        );

        // When
        ResponseEntity<String> response = restTemplate
                .postForEntity("/api/tickets", request, String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void healthCheckDebeEstarDisponibleSinAuth() {
        // When
        ResponseEntity<String> response = restTemplate
                .getForEntity("/api/health", String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
