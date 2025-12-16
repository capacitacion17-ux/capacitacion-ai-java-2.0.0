# 📐 Arquitectura del Sistema - Ticketero con Notificaciones

**Proyecto:** Sistema de Ticketero con Notificaciones Telegram
**Versión:** 1.0
**Fecha:** Noviembre 2025
**Tipo:** Proyecto de Capacitación

---

## 📑 Contenido

1. [Visión General](#1-visión-general)
2. [Arquitectura de Alto Nivel](#2-arquitectura-de-alto-nivel)
3. [Componentes Principales](#3-componentes-principales)
4. [Flujo End-to-End](#4-flujo-end-to-end)
5. [Modelo de Datos](#5-modelo-de-datos)
6. [Stack Tecnológico](#6-stack-tecnológico)
7. [Decisiones de Arquitectura](#7-decisiones-de-arquitectura)
8. [Configuración y Deployment](#8-configuración-y-deployment)

---

## 1. Visión General

### 1.1 ¿Qué es este sistema?

Sistema que **digitaliza el proceso de emisión de tickets** en sucursales bancarias, enviando **3 notificaciones automáticas** vía Telegram:

1. **Confirmación inmediata** - "Tu ticket P01 está listo, tiempo estimado: 15 min"
2. **Pre-aviso (~30s después)** - "Faltan 3 turnos para ti"
3. **Turno activo (~60s después)** - "¡Es tu turno P01!"

### 1.2 Objetivos

- ✅ Implementar API REST funcional end-to-end
- ✅ Integración real con Telegram
- ✅ Código limpio siguiendo mejores prácticas
- ✅ Arquitectura pragmática y mantenible

### 1.3 Alcance

**✅ Incluido:**
- API REST (2 endpoints)
- Base de datos (2 tablas)
- Integración real Telegram
- Scheduler de mensajes
- Docker Compose
- Validaciones básicas
- Tests unitarios básicos

**❌ NO Incluido (para mantener simplicidad):**
- Circuit breaker / Resilience4j
- Métricas custom avanzadas
- Validadores custom con anotaciones propias
- TestContainers
- BaseEntity abstracta
- Logging JSON estructurado

---

## 2. Arquitectura de Alto Nivel

### 2.1 Diagrama de Contexto

```
┌──────────────────────────────────────────────────────────────┐
│                         USUARIOS                             │
│   👤 Usuario en Sucursal          💻 Ejecutivo               │
└────────────┬─────────────────────────────┬───────────────────┘
             │                             │
             │ 1. Ingresa ID + teléfono    │ 5. Atiende
             ▼                             │
┌─────────────────────────────────────────┐│
│   🖥️  TICKETERO (Simulado: Postman)     ││
└────────────┬─────────────────────────────┘│
             │                              │
             │ 2. POST /api/ticket          │
             ▼                              │
┌──────────────────────────────────────────┴────────────────┐
│              🎯 API REST (Spring Boot)                     │
│  • TicketController                                        │
│  • TicketService                                           │
│  • TelegramService                                         │
│  • MessageScheduler                                        │
└──────┬──────────────────────┬──────────────────────────────┘
       │                      │
       │ 3. JDBC             │ 4. HTTPS
       ▼                      ▼
┌─────────────┐      ┌──────────────────┐
│ PostgreSQL  │      │  Telegram API    │
│ • ticket    │      │  api.telegram.org│
│ • mensaje   │      └─────────┬────────┘
└─────────────┘                │
                               │ Push
                               ▼
                     ┌────────────────┐
                     │ Usuario        │
                     │ Telegram App   │
                     └────────────────┘
```

### 2.2 Arquitectura Interna (Capas)

```
┌────────────────────────────────────────────────┐
│         CONTROLLERS (REST)                     │
│  TicketController, InteraccionController       │
└──────────────────┬─────────────────────────────┘
                   ▼
┌────────────────────────────────────────────────┐
│         SERVICES (Lógica de Negocio)           │
│  TicketService, TelegramService                │
└──────────────────┬─────────────────────────────┘
                   ▼
┌────────────────────────────────────────────────┐
│         REPOSITORIES (Data Access)             │
│  TicketRepository, MensajeRepository           │
└──────────────────┬─────────────────────────────┘
                   ▼
┌────────────────────────────────────────────────┐
│         DATABASE (PostgreSQL)                  │
│  ticket, mensaje                               │
└────────────────────────────────────────────────┘

┌────────────────────────────────────────────────┐
│         SCHEDULER (Async)                      │
│  MessageScheduler → @Scheduled(fixedDelay=5s)  │
└────────────────────────────────────────────────┘
```

---

## 3. Componentes Principales

### 3.1 Controllers (Capa de Presentación)

#### TicketController
```java
@RestController
@RequestMapping("/api")
public class TicketController {
    
    @PostMapping("/ticket")
    public ResponseEntity<TicketResponse> crearTicket(
        @Valid @RequestBody TicketRequest request
    ) {
        // Validar, crear ticket, programar mensajes
    }
}
```

**Responsabilidad:**
- Recibir requests HTTP
- Validar datos de entrada (`@Valid`)
- Delegar a service
- Retornar respuesta

### 3.2 Services (Capa de Negocio)

#### TicketService
```java
@Service
public class TicketService {
    
    @Transactional
    public TicketResponse crearTicket(TicketRequest request) {
        // 1. Crear ticket en BD
        // 2. Crear 3 mensajes programados
        // 3. Retornar respuesta
    }
}
```

**Responsabilidad:**
- Lógica de negocio principal
- Orquestar creación de ticket
- Programar los 3 mensajes

#### TelegramService
```java
@Service
public class TelegramService {
    
    private final RestTemplate restTemplate;
    
    public void enviarMensaje(String chatId, String texto) {
        String url = telegramApiUrl + "/sendMessage";
        // HTTP POST a Telegram
    }
}
```

**Responsabilidad:**
- Cliente HTTP para Telegram
- Envío de mensajes
- Manejo de errores básico

### 3.3 Repositories (Capa de Datos)

```java
@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    Optional<Ticket> findByCodigoReferencia(UUID codigo);
}

@Repository
public interface MensajeRepository extends JpaRepository<Mensaje, Long> {
    List<Mensaje> findByEstadoEnvioAndFechaProgramadaLessThanEqual(
        String estado, LocalDateTime fecha
    );
}
```

**Responsabilidad:**
- CRUD operations
- Queries custom con naming convention

### 3.4 Scheduler (Procesamiento Asíncrono)

```java
@Component
public class MessageScheduler {
    
    @Scheduled(fixedDelay = 5000) // Cada 5 segundos
    public void procesarMensajesPendientes() {
        // 1. Buscar mensajes con estado=PENDIENTE y fecha <= NOW
        // 2. Enviar via TelegramService
        // 3. Actualizar estado a ENVIADO o FALLIDO
    }
}
```

**Responsabilidad:**
- Ejecutar cada 5 segundos
- Procesar mensajes programados
- Actualizar estados

---

## 4. Flujo End-to-End

### 4.1 Secuencia Completa

```
[Usuario en Sucursal]
       │
       │ 1. Ingresa datos en ticketero
       ▼
[Ticketero/Postman] ──────POST /api/ticket────────▶ [TicketController]
                          {nationalId, telefono}              │
                                                               │ @Valid
                                                               ▼
                                                        [TicketService]
                                                               │
                            ┌──────────────────────────────────┤
                            │                                  │
                            ▼                                  ▼
                    [TicketRepository]                 [MensajeRepository]
                            │                                  │
                            │ INSERT ticket                    │ INSERT 3 mensajes
                            │ codigo_ref=UUID                  │ • Msg1: NOW()
                            │                                  │ • Msg2: NOW()+30s
                            │                                  │ • Msg3: NOW()+60s
                            ▼                                  ▼
                      [PostgreSQL]                        [PostgreSQL]
                            
                            
[MessageScheduler] ──────cada 5s──────▶ SELECT mensajes WHERE
   @Scheduled                            estado='PENDIENTE' AND
                                         fecha_programada <= NOW()
       │
       │ Encuentra Mensaje 1
       ▼
[TelegramService] ──────HTTP POST────────▶ [Telegram API]
   enviarMensaje()        sendMessage              │
                                                   │ Push notification
                                                   ▼
                                         [Usuario - Telegram App]
                                           "🎫 Tu turno P01..."


... ~30 segundos después, Scheduler detecta Mensaje 2 ...
... ~30 segundos después, Scheduler detecta Mensaje 3 ...
```

### 4.2 Estados de Mensaje

```
PENDIENTE ──(scheduler detecta)──▶ ENVIANDO ──(éxito)──▶ ENVIADO
                                      │
                                      └──(fallo)──▶ FALLIDO
```

---

## 5. Modelo de Datos

### 5.1 Diagrama ER

```
┌─────────────────────────┐
│        ticket           │
├─────────────────────────┤
│ id (PK)                 │
│ codigo_referencia (UQ)  │◀───┐
│ national_id             │    │
│ telefono                │    │
│ branch_office           │    │ 1:N
│ numero (ej: P01)        │    │
│ queue                   │    │
│ status                  │    │
│ created_at              │    │
└─────────────────────────┘    │
                               │
┌─────────────────────────┐    │
│       mensaje           │    │
├─────────────────────────┤    │
│ id (PK)                 │    │
│ ticket_id (FK) ─────────┼────┘
│ plantilla               │
│ medio_envio (TELEGRAM)  │
│ estado_envio            │
│ fecha_programada        │
│ fecha_envio             │
│ telegram_message_id     │
│ intentos                │
│ created_at              │
└─────────────────────────┘
```

### 5.2 Tabla: ticket

| Campo | Tipo | Descripción |
|-------|------|-------------|
| id | BIGSERIAL | PK autoincremental |
| codigo_referencia | UUID | Identificador único para tracking |
| national_id | VARCHAR(20) | ID del usuario (varía por país) |
| telefono | VARCHAR(15) | Teléfono (opcional) |
| branch_office | VARCHAR(100) | Sucursal |
| numero | VARCHAR(4) | Número de ticket (P01, E12) |
| queue | VARCHAR(50) | Cola de atención |
| status | VARCHAR(20) | CREATED, NOTIFIED, COMPLETED |
| created_at | TIMESTAMP | Fecha de creación |

**Índices:**
- `idx_ticket_codigo_ref` on codigo_referencia (UNIQUE)
- `idx_ticket_national_id` on national_id

### 5.3 Tabla: mensaje

| Campo | Tipo | Descripción |
|-------|------|-------------|
| id | BIGSERIAL | PK autoincremental |
| ticket_id | BIGINT | FK a ticket.id |
| plantilla | VARCHAR(50) | Template del mensaje |
| medio_envio | VARCHAR(20) | TELEGRAM (fijo) |
| estado_envio | VARCHAR(20) | PENDIENTE, ENVIADO, FALLIDO |
| fecha_programada | TIMESTAMP | Cuándo enviarlo |
| fecha_envio | TIMESTAMP | Cuándo se envió (null si no) |
| telegram_message_id | VARCHAR(50) | ID de Telegram |
| intentos | INTEGER | Contador de reintentos |
| created_at | TIMESTAMP | Fecha de creación |

**Índices:**
- `idx_mensaje_ticket_id` on ticket_id
- `idx_mensaje_estado` on estado_envio
- `idx_mensaje_programada` on fecha_programada

**Foreign Key:**
- `fk_mensaje_ticket` (ticket_id → ticket.id) ON DELETE CASCADE

---

## 6. Stack Tecnológico

### 6.1 Backend (Core)

| Tecnología | Versión | Propósito |
|------------|---------|-----------|
| **Java** | 21 (LTS) | Lenguaje principal |
| **Spring Boot** | 3.2+ | Framework web |
| **Spring Data JPA** | 3.2+ | ORM / Persistencia |
| **Spring Validation** | 3.2+ | Validación de DTOs |
| **Lombok** | 1.18+ | Reduce boilerplate |

### 6.2 Base de Datos

| Tecnología | Versión | Propósito |
|------------|---------|-----------|
| **PostgreSQL** | 16 | Base de datos relacional |
| **Flyway** | 10+ | Migrations (opcional: puedes usar schema.sql) |

### 6.3 Integración Externa

| Servicio | URL | Propósito |
|----------|-----|-----------|
| **Telegram Bot API** | https://api.telegram.org | Envío de mensajes |

### 6.4 DevOps

| Herramienta | Propósito |
|-------------|-----------|
| **Docker** | Contenedores |
| **Docker Compose** | Orquestación local |
| **Maven** | Build tool |

### 6.5 Documentación (Opcional)

| Herramienta | Propósito |
|-------------|-----------|
| **SpringDoc OpenAPI** | Swagger UI (opcional, nice to have) |

### 6.6 Testing (Básico)

| Herramienta | Propósito |
|-------------|-----------|
| **JUnit 5** | Framework de testing |
| **Mockito** | Mocking para unit tests |

---

## 7. Decisiones de Arquitectura

### ADR-001: Telegram vs WhatsApp

**Decisión:** Usar Telegram Bot API  
**Razón:** Setup inmediato, gratis, funcional end-to-end  
**Trade-off:** Menor adopción que WhatsApp en usuarios finales

**Alternativas consideradas:**
- WhatsApp Business API → Descartado: complejo, costoso
- SMS → Descartado: costo por mensaje

---

### ADR-002: Mensajes Programados con Scheduler

**Decisión:** Spring @Scheduled con polling de BD cada 5s  
**Razón:** Simple, confiable, no requiere dependencias adicionales  
**Trade-off:** Latencia máxima de 5s (aceptable para este caso)

**Alternativas consideradas:**
- Quartz Jobs → Overkill para este volumen
- Redis + TTL → Dependencia adicional innecesaria

---

### ADR-003: Sin Circuit Breaker para MVP

**Decisión:** NO usar Resilience4j en primera versión
**Razón:** Mantener simplicidad en MVP - agregar según necesidad real
**Trade-off:** Si Telegram falla, los mensajes quedan en FALLIDO (se puede reintentar manualmente)

**Futuro:** Agregar en Fase 2 si se detecta necesidad real

---

### ADR-004: DTOs Simples sin Mappers

**Decisión:** DTOs básicos, mapeo manual en services  
**Razón:** Evitar dependencias como MapStruct  
**Trade-off:** Código de mapeo manual (pero simple y claro)

---

### ADR-005: Validación con Bean Validation estándar

**Decisión:** Usar anotaciones estándar (`@NotBlank`, `@Pattern`)  
**Razón:** No crear validadores custom con anotaciones propias  
**Trade-off:** Menos "elegante" pero más simple

**Ejemplo:**
```java
// ✅ SIMPLE
@Pattern(regexp = "^[0-9]{8,12}$", message = "ID nacional inválido")
private String nationalId;

// ❌ OVER-ENGINEERING (evitar)
@ValidNationalId
private String nationalId;
```

---

### ADR-006: Sin BaseEntity abstracta

**Decisión:** Cada entidad define sus propios campos  
**Razón:** Solo 2 entidades, no justifica abstracción  
**Trade-off:** Duplicación de campos `created_at` (aceptable)

---

### ADR-007: RestTemplate en lugar de WebClient

**Decisión:** Usar RestTemplate para llamadas HTTP  
**Razón:** Más simple que WebClient reactivo  
**Trade-off:** No reactivo (no necesario para este volumen)

---

## 8. Configuración y Deployment

### 8.1 Variables de Entorno

| Variable | Descripción | Ejemplo | Requerido |
|----------|-------------|---------|-----------|
| `TELEGRAM_BOT_TOKEN` | Token del bot | `123456:ABC-DEF...` | ✅ Sí |
| `DATABASE_URL` | URL PostgreSQL | `jdbc:postgresql://...` | ✅ Sí |
| `DATABASE_USERNAME` | Usuario BD | `ticketero_user` | ✅ Sí |
| `DATABASE_PASSWORD` | Password BD | `***` | ✅ Sí |
| `API_KEY` | API Key (opcional) | `secret-key-123` | ❌ No (para demo) |

### 8.2 Docker Compose

```yaml
version: '3.8'

services:
  api:
    build: .
    ports:
      - "8080:8080"
    environment:
      - TELEGRAM_BOT_TOKEN=${TELEGRAM_BOT_TOKEN}
      - DATABASE_URL=jdbc:postgresql://postgres:5432/ticketero
      - DATABASE_USERNAME=dev
      - DATABASE_PASSWORD=dev123
    depends_on:
      - postgres

  postgres:
    image: postgres:16
    ports:
      - "5432:5432"
    environment:
      - POSTGRES_DB=ticketero
      - POSTGRES_USER=dev
      - POSTGRES_PASSWORD=dev123
    volumes:
      - pgdata:/var/lib/postgresql/data

volumes:
  pgdata:
```

### 8.3 Application Properties

```yaml
# application.yml
spring:
  application:
    name: ticketero-api
  
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
  
  jpa:
    hibernate:
      ddl-auto: validate # Flyway maneja schema
    show-sql: true

telegram:
  bot-token: ${TELEGRAM_BOT_TOKEN}
  api-url: https://api.telegram.org/bot
```

---

## 9. Patrones y Mejores Prácticas

### 9.1 Patrones Aplicados

1. **Layered Architecture** - Controller → Service → Repository
2. **Repository Pattern** - Spring Data JPA
3. **DTO Pattern** - Request/Response separados de entidades
4. **Dependency Injection** - Constructor injection con Spring

### 9.2 Principios SOLID (Simplificados)

- **Single Responsibility:** Cada clase tiene una responsabilidad
- **Dependency Inversion:** Servicios dependen de interfaces (repositories)

### 9.3 Convenciones de Código

- ✅ Usar Lombok para reducir boilerplate
- ✅ Constructor injection (no @Autowired en fields)
- ✅ Métodos pequeños y descriptivos
- ✅ Logging en puntos clave
- ✅ Manejo de excepciones en controller advice

---

## 10. Testing (Básico)

### 10.1 Tests Unitarios

```java
@ExtendWith(MockitoExtension.class)
class TicketServiceTest {
    
    @Mock
    private TicketRepository ticketRepository;
    
    @Mock
    private TelegramService telegramService;
    
    @InjectMocks
    private TicketService ticketService;
    
    @Test
    void crearTicket_conDatosValidos_debeRetornarResponse() {
        // Given
        TicketRequest request = new TicketRequest(...);
        
        // When
        TicketResponse response = ticketService.crearTicket(request);
        
        // Then
        assertNotNull(response.getIdentificador());
        verify(ticketRepository).save(any());
    }
}
```

### 10.2 Cobertura Objetivo

- **Services:** 60-70% (lo esencial)
- **Controllers:** Opcional
- **Repositories:** No necesario (Spring Data)

---

## 11. Seguridad Básica

### 11.1 Validación de Inputs

```java
@PostMapping("/api/ticket")
public ResponseEntity<TicketResponse> crearTicket(
    @Valid @RequestBody TicketRequest request  // Bean Validation
) {
    // Spring valida automáticamente
}
```

### 11.2 Datos Sensibles

- ❌ NO loggear teléfonos completos
- ❌ NO commitear tokens en código
- ✅ Usar variables de entorno para secrets

---

## 12. Limitaciones y Futuras Mejoras

### 12.1 Limitaciones Conocidas (Aceptables para MVP)

- Sin autenticación/autorización real (solo para demo)
- Sin retry automático en fallos de Telegram
- Sin circuit breaker
- Sin métricas avanzadas
- Sin high availability setup

### 12.2 Roadmap Futuro

**Fase 2:**
- Agregar Resilience4j (circuit breaker)
- Tests de integración con TestContainers
- Métricas custom con Micrometer

**Fase 3:**
- Migrar a WhatsApp Business API
- Dashboard administrativo
- Kubernetes deployment

---

## 13. Referencias

- **Spring Boot Docs:** https://spring.io/projects/spring-boot
- **Telegram Bot API:** https://core.telegram.org/bots/api
- **PostgreSQL Docs:** https://www.postgresql.org/docs/16/
- **Checklist de Implementación:** Ver `IMPLEMENTATION-CHECKLIST.md`

---

## 14. Resumen Ejecutivo

### ✅ Lo que TIENES

- API REST funcional con Spring Boot
- Integración REAL con Telegram (end-to-end)
- Base de datos PostgreSQL con 2 tablas
- Scheduler para mensajes programados
- Docker Compose listo para usar
- Arquitectura limpia y simple

### ❌ Lo que NO TIENES (y está bien)

- Circuit breakers (no necesario para MVP)
- Métricas custom avanzadas (Actuator básico es suficiente)
- Validadores custom con anotaciones propias (regex es suficiente)
- BaseEntity abstracta (solo 2 entidades)
- TestContainers (H2 in-memory es suficiente para tests)

### 🎯 Filosofía

Sistema simple, profesional, funcional y fácil de entender.

---

**Fin del Documento de Arquitectura**

_Versión 1.0 - Noviembre 2025_




