package com.example.ticketero.integration;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.TimeUnit;

/**
 * Base class for all integration tests.
 * Provides TestContainers setup and common utilities.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @LocalServerPort
    protected int port;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    // ============================================================
    // TESTCONTAINERS
    // ============================================================

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("ticketero_test")
        .withUsername("test")
        .withPassword("test");

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management-alpine")
        .withExposedPorts(5672, 15672);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // RabbitMQ
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");

        // Telegram Mock (WireMock)
        registry.add("telegram.api-url", () -> "http://localhost:8089/bot");
        registry.add("telegram.bot-token", () -> "test-token");
        registry.add("telegram.chat-id", () -> "123456789");
    }

    // ============================================================
    // SETUP
    // ============================================================

    @BeforeAll
    static void setupContainers() {
        postgres.start();
        rabbitmq.start();
    }

    @BeforeEach
    void setupRestAssured() {
        RestAssured.port = port;
        RestAssured.basePath = "/api";
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @BeforeEach
    void cleanDatabase() {
        // Limpiar en orden correcto (FK constraints)
        jdbcTemplate.execute("DELETE FROM ticket_event");
        jdbcTemplate.execute("DELETE FROM recovery_event");
        jdbcTemplate.execute("DELETE FROM outbox_message");
        jdbcTemplate.execute("DELETE FROM ticket");
        jdbcTemplate.execute("UPDATE advisor SET status = 'AVAILABLE', total_tickets_served = 0");
    }

    // ============================================================
    // UTILITIES
    // ============================================================

    protected String createTicketRequest(String nationalId, String telefono, 
                                          String branchOffice, String queueType) {
        return String.format("""
            {
                "nationalId": "%s",
                "telefono": "%s",
                "branchOffice": "%s",
                "queueType": "%s"
            }
            """, nationalId, telefono, branchOffice, queueType);
    }

    protected String createTicketRequest(String nationalId, String queueType) {
        return createTicketRequest(nationalId, "+56912345678", "Sucursal Centro", queueType);
    }

    protected int countTicketsInStatus(String status) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ticket WHERE status = ?",
            Integer.class, status);
    }

    protected int countOutboxMessages(String status) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outbox_message WHERE status = ?",
            Integer.class, status);
    }

    protected int countAdvisorsInStatus(String status) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM advisor WHERE status = ?",
            Integer.class, status);
    }

    protected void waitForTicketProcessing(int expectedCompleted, int timeoutSeconds) {
        org.awaitility.Awaitility.await()
            .atMost(timeoutSeconds, TimeUnit.SECONDS)
            .pollInterval(500, TimeUnit.MILLISECONDS)
            .until(() -> countTicketsInStatus("COMPLETED") >= expectedCompleted);
    }

    protected void setAdvisorStatus(Long advisorId, String status) {
        jdbcTemplate.update(
            "UPDATE advisor SET status = ? WHERE id = ?",
            status, advisorId);
    }
}