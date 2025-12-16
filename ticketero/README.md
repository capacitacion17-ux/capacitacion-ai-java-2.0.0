# Ticketero - Queue Management System

A robust ticket queue management system with Telegram notifications, built with Spring Boot 3.2 and modern Java 21 features.

## Architecture

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Client    │────▶│  REST API   │────▶│  PostgreSQL │
└─────────────┘     └──────┬──────┘     └─────────────┘
                          │
                    ┌─────▼─────┐
                    │  Outbox   │
                    │  Pattern  │
                    └─────┬─────┘
                          │
                    ┌─────▼─────┐     ┌─────────────┐
                    │ RabbitMQ  │────▶│   Workers   │
                    └───────────┘     └──────┬──────┘
                                             │
                                      ┌──────▼──────┐
                                      │  Telegram   │
                                      │    Bot      │
                                      └─────────────┘
```

## Features

- **Queue Management**: Real-time ticket positioning with estimated wait times
- **Outbox Pattern**: Transactional consistency between PostgreSQL and RabbitMQ
- **Telegram Notifications**: Three notification types (created, upcoming, called)
- **Auto-Recovery**: Automatic detection and recovery of dead workers
- **Metrics**: Prometheus-compatible metrics for monitoring

## Tech Stack

| Component | Technology |
|-----------|------------|
| Runtime | Java 21 |
| Framework | Spring Boot 3.2 |
| Database | PostgreSQL 16 |
| Messaging | RabbitMQ 3.13 |
| Migrations | Flyway |
| Metrics | Micrometer + Prometheus |
| Containerization | Docker + Docker Compose |

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker & Docker Compose
- Telegram Bot Token (for notifications)

## Quick Start

### 1. Clone and Configure

```bash
cd ticketero
cp .env.example .env
# Edit .env with your Telegram credentials
```

### 2. Start Services

```bash
docker compose up -d
```

### 3. Verify Health

```bash
curl http://localhost:8080/actuator/health
```

## API Endpoints

### Tickets

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/tickets` | Create new ticket |
| GET | `/api/tickets/{uuid}` | Get ticket by reference code |
| GET | `/api/tickets/{numero}/position` | Get queue position |

### Admin Dashboard

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/dashboard` | System overview |
| GET | `/api/admin/queues/{type}` | Queue status |
| GET | `/api/admin/advisors` | List advisors |
| PUT | `/api/admin/advisors/{id}/status` | Update advisor status |

## Project Structure

```
src/main/java/com/example/ticketero/
├── config/          # Spring configurations
├── consumer/        # RabbitMQ message consumers
├── controller/      # REST API controllers
├── exception/       # Custom exceptions
├── model/
│   ├── dto/         # Data Transfer Objects
│   ├── entity/      # JPA entities
│   └── enums/       # Enumeration types
├── repository/      # Spring Data repositories
├── service/         # Business logic services
└── util/            # Utility classes
```

## Key Patterns

### Outbox Pattern

Ensures transactional consistency between database and message broker:

```java
@Transactional
public TicketResponse crearTicket(TicketCreateRequest request) {
    Ticket ticket = ticketRepository.saveAndFlush(ticket);
    outboxMessageRepository.save(outboxMessage);  // Same transaction
    return response;
}
```

### Manual ACK with RabbitMQ

Prevents message loss with manual acknowledgment:

```java
@RabbitListener(queues = "caja-queue", ackMode = "MANUAL")
public void process(Message msg, Channel channel, long deliveryTag) {
    try {
        processTicket(msg);
        channel.basicAck(deliveryTag, false);
    } catch (Exception e) {
        channel.basicNack(deliveryTag, false, true);
    }
}
```

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DATABASE_URL` | PostgreSQL connection URL | `jdbc:postgresql://localhost:5432/ticketero` |
| `DATABASE_USERNAME` | Database user | `dev` |
| `DATABASE_PASSWORD` | Database password | `dev123` |
| `RABBITMQ_HOST` | RabbitMQ host | `localhost` |
| `TELEGRAM_BOT_TOKEN` | Telegram bot token | - |
| `TELEGRAM_CHAT_ID` | Telegram chat ID | - |

## Development

### Run Tests

```bash
./mvnw test
```

### Build JAR

```bash
./mvnw clean package -DskipTests
```

### Run Locally

```bash
./mvnw spring-boot:run
```

## Monitoring

### Health Check

```bash
curl http://localhost:8080/actuator/health
```

### Prometheus Metrics

```bash
curl http://localhost:8080/actuator/prometheus
```

### RabbitMQ Management

Open http://localhost:15672 (user: dev, password: dev123)

## License

This project is for educational purposes as part of Java developer training.
