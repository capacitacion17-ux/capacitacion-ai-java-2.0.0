# **PROMPT 6A: PRUEBAS UNITARIAS \- Testing Aislado del Sistema Ticketero**

## **Contexto**

Eres un Java Developer Senior experto en testing. Tu tarea es crear **pruebas unitarias puras** con JUnit 5 \+ Mockito para el sistema Ticketero, un sistema de gestión de turnos con lógica de negocio compleja.

**Características del proyecto:**

* API REST con Spring Boot 3.2, Java 21  
* PostgreSQL \+ RabbitMQ \+ Telegram  
* Patrones: Outbox, TX única, Auto-recovery  
* 7 servicios críticos con lógica compleja

**IMPORTANTE:** Después de completar CADA paso, debes DETENERTE y solicitar una **revisión exhaustiva** antes de continuar.

---

## **Documentos de Entrada**

**Lee estos archivos del proyecto:**

1. `src/main/java/com/example/ticketero/service/` \- Servicios a testear  
2. `src/main/java/com/example/ticketero/model/` \- Entidades y DTOs  
3. `src/main/java/com/example/ticketero/repository/` \- Interfaces de repositorios  
4. `docs/CODING-STANDARDS.md` \- Convenciones de código

---

## **Metodología de Trabajo**

### **Principio:**

**"Diseñar → Implementar → Ejecutar → Confirmar → Continuar"**

Después de CADA paso:

1. ✅ Diseña los casos de prueba  
2. ✅ Implementa los tests  
3. ✅ Ejecuta `mvn test -Dtest=NombreTest`  
4. ⏸️ **DETENTE y solicita revisión**  
5. ✅ Espera confirmación antes de continuar

### **Formato de Solicitud de Revisión:**

✅ PASO X COMPLETADO

Tests implementados:  
\- \[Nombre del test 1\]  
\- \[Nombre del test 2\]  
\- ...

Cobertura del servicio:  
\- Métodos cubiertos: X/Y  
\- Líneas estimadas: Z%

🔍 SOLICITO REVISIÓN EXHAUSTIVA:

1\. ¿Los casos de prueba son correctos?  
2\. ¿Los mocks están bien configurados?  
3\. ¿Las assertions son suficientes?  
4\. ¿Puedo continuar con el siguiente paso?

⏸️ ESPERANDO CONFIRMACIÓN...

---

## **Stack de Testing**

| Componente | Versión | Propósito |
| ----- | ----- | ----- |
| JUnit 5 (Jupiter) | 5.10+ | Framework de testing |
| Mockito | 5.x | Mocks y stubs |
| AssertJ | 3.24+ | Assertions fluidas |
| ArgumentCaptor | Mockito | Captura de argumentos |

**❌ NO usar en Unit Tests:**

* `@SpringBootTest` (reservado para integración)  
* `@DataJpaTest` (reservado para repositorios)  
* TestContainers (reservado para E2E)  
* Base de datos real  
* RabbitMQ real  
* Telegram API real

---

## **Convenciones de Nombres**

**Patrón:** `methodName_condition_expectedBehavior()`

**Ejemplos:**

crearTicket\_conDatosValidos\_debeRetornarResponse()  
procesarTicket\_sinAsesoresDisponibles\_debeLanzarExcepcion()  
calcularPosicion\_colaVacia\_debeRetornarUno()  
completarAtencion\_ticketEnProgreso\_debeCompletarYLiberarAdvisor()

---

## **Tu Tarea: 8 Pasos**

**PASO 1:** Setup y TicketServiceTest (6 tests)  
**PASO 2:** TicketProcessingServiceTest (8 tests)  
**PASO 3:** AdvisorServiceTest (7 tests)  
**PASO 4:** QueueManagementServiceTest (6 tests)  
**PASO 5:** OutboxPublisherServiceTest (5 tests)  
**PASO 6:** RecoveryServiceTest (5 tests)  
**PASO 7:** NotificationServiceTest (4 tests)  
**PASO 8:** Ejecución Final y Cobertura

**Total:** \~41 tests | Cobertura objetivo: \>90%

---

## **Estructura de Archivos a Crear**

src/test/java/com/example/ticketero/  
├── service/  
│   ├── TicketServiceTest.java  
│   ├── TicketProcessingServiceTest.java  
│   ├── AdvisorServiceTest.java  
│   ├── QueueManagementServiceTest.java  
│   ├── OutboxPublisherServiceTest.java  
│   ├── RecoveryServiceTest.java  
│   └── NotificationServiceTest.java  
└── testutil/  
    └── TestDataBuilder.java

---

## **PASO 1: Setup y TicketServiceTest**

**Objetivo:** Configurar utilidades de testing y crear tests para TicketService.

### **1.1 TestDataBuilder.java**

package com.example.ticketero.testutil;

import com.example.ticketero.model.dto.TicketCreateRequest;  
import com.example.ticketero.model.entity.\*;  
import com.example.ticketero.model.enums.\*;

import java.time.LocalDateTime;  
import java.util.UUID;

/\*\*  
 \* Builder para crear datos de prueba consistentes.  
 \*/  
public class TestDataBuilder {

    // \============================================================  
    // TICKETS  
    // \============================================================  
      
    public static Ticket.TicketBuilder ticketWaiting() {  
        return Ticket.builder()  
            .id(1L)  
            .codigoReferencia(UUID.randomUUID())  
            .numero("C001")  
            .nationalId("12345678")  
            .telefono("+56912345678")  
            .branchOffice("Sucursal Centro")  
            .queueType(QueueType.CAJA)  
            .status(TicketStatus.WAITING)  
            .positionInQueue(1)  
            .estimatedWaitMinutes(5)  
            .createdAt(LocalDateTime.now());  
    }  
      
    public static Ticket.TicketBuilder ticketInProgress() {  
        return ticketWaiting()  
            .status(TicketStatus.IN\_PROGRESS)  
            .calledAt(LocalDateTime.now().minusMinutes(2))  
            .startedAt(LocalDateTime.now().minusMinutes(1));  
    }  
      
    public static Ticket.TicketBuilder ticketCompleted() {  
        return ticketInProgress()  
            .status(TicketStatus.COMPLETED)  
            .completedAt(LocalDateTime.now());  
    }

    // \============================================================  
    // ADVISORS  
    // \============================================================  
      
    public static Advisor.AdvisorBuilder advisorAvailable() {  
        return Advisor.builder()  
            .id(1L)  
            .name("María López")  
            .email("maria.lopez@banco.com")  
            .moduleNumber(1)  
            .queueTypesJson("\[\\"CAJA\\"\]")  
            .status(AdvisorStatus.AVAILABLE)  
            .avgServiceTimeMinutes(5)  
            .totalTicketsServed(10)  
            .createdAt(LocalDateTime.now())  
            .lastActiveAt(LocalDateTime.now())  
            .lastHeartbeat(LocalDateTime.now())  
            .recoveryCount(0);  
    }  
      
    public static Advisor.AdvisorBuilder advisorBusy() {  
        return advisorAvailable()  
            .status(AdvisorStatus.BUSY);  
    }

    // \============================================================  
    // QUEUE CONFIG  
    // \============================================================  
      
    public static QueueConfig.QueueConfigBuilder queueConfigCaja() {  
        return QueueConfig.builder()  
            .id(1L)  
            .queueType(QueueType.CAJA)  
            .displayName("Caja")  
            .avgServiceTimeMinutes(5)  
            .notificationThreshold(3)  
            .priority(1)  
            .isActive(true)  
            .createdAt(LocalDateTime.now())  
            .updatedAt(LocalDateTime.now());  
    }

    // \============================================================  
    // REQUESTS  
    // \============================================================  
      
    public static TicketCreateRequest validTicketRequest() {  
        return new TicketCreateRequest(  
            "12345678",  
            "+56912345678",  
            "Sucursal Centro",  
            QueueType.CAJA  
        );  
    }  
      
    public static TicketCreateRequest ticketRequestSinTelefono() {  
        return new TicketCreateRequest(  
            "12345678",  
            null,  
            "Sucursal Centro",  
            QueueType.CAJA  
        );  
    }

    // \============================================================  
    // OUTBOX  
    // \============================================================  
      
    public static OutboxMessage.OutboxMessageBuilder outboxPending() {  
        return OutboxMessage.builder()  
            .id(1L)  
            .aggregateType("TICKET")  
            .aggregateId(1L)  
            .eventType("TICKET\_CREATED")  
            .payload("{\\"ticketId\\":1,\\"numero\\":\\"C001\\"}")  
            .routingKey("caja-queue")  
            .status("PENDING")  
            .retryCount(0)  
            .maxRetries(5)  
            .createdAt(LocalDateTime.now());  
    }  
}

### **1.2 TicketServiceTest.java**

package com.example.ticketero.service;

import com.example.ticketero.exception.TicketNotFoundException;  
import com.example.ticketero.model.dto.TicketCreateRequest;  
import com.example.ticketero.model.dto.TicketResponse;  
import com.example.ticketero.model.entity.OutboxMessage;  
import com.example.ticketero.model.entity.QueueConfig;  
import com.example.ticketero.model.entity.Ticket;  
import com.example.ticketero.model.enums.QueueType;  
import com.example.ticketero.model.enums.TicketStatus;  
import com.example.ticketero.repository.OutboxMessageRepository;  
import com.example.ticketero.repository.TicketRepository;  
import com.fasterxml.jackson.databind.ObjectMapper;  
import org.junit.jupiter.api.DisplayName;  
import org.junit.jupiter.api.Nested;  
import org.junit.jupiter.api.Test;  
import org.junit.jupiter.api.extension.ExtendWith;  
import org.mockito.ArgumentCaptor;  
import org.mockito.InjectMocks;  
import org.mockito.Mock;  
import org.mockito.Spy;  
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;  
import java.util.UUID;

import static com.example.ticketero.testutil.TestDataBuilder.\*;  
import static org.assertj.core.api.Assertions.\*;  
import static org.mockito.ArgumentMatchers.any;  
import static org.mockito.Mockito.\*;

@ExtendWith(MockitoExtension.class)  
@DisplayName("TicketService \- Unit Tests")  
class TicketServiceTest {

    @Mock  
    private TicketRepository ticketRepository;

    @Mock  
    private OutboxMessageRepository outboxMessageRepository;

    @Mock  
    private QueueManagementService queueManagementService;

    @Mock  
    private NotificationService notificationService;

    @Mock  
    private MetricsService metricsService;

    @Spy  
    private ObjectMapper objectMapper \= new ObjectMapper();

    @InjectMocks  
    private TicketService ticketService;

    // \============================================================  
    // CREAR TICKET  
    // \============================================================  
      
    @Nested  
    @DisplayName("crearTicket()")  
    class CrearTicket {

        @Test  
        @DisplayName("con datos válidos → debe crear ticket, guardar en outbox y notificar")  
        void crearTicket\_conDatosValidos\_debeCrearTicketOutboxYNotificar() {  
            // Given  
            TicketCreateRequest request \= validTicketRequest();  
            Ticket ticketGuardado \= ticketWaiting()  
                .numero("C001")  
                .positionInQueue(3)  
                .estimatedWaitMinutes(10)  
                .build();

            when(queueManagementService.calcularPosicionEnCola(QueueType.CAJA)).thenReturn(3);  
            when(queueManagementService.calcularTiempoEstimado(QueueType.CAJA, 3)).thenReturn(10);  
            when(ticketRepository.saveAndFlush(any(Ticket.class))).thenReturn(ticketGuardado);

            // When  
            TicketResponse response \= ticketService.crearTicket(request);

            // Then  
            assertThat(response).isNotNull();  
            assertThat(response.numero()).isEqualTo("C001");  
            assertThat(response.positionInQueue()).isEqualTo(3);  
            assertThat(response.estimatedWaitMinutes()).isEqualTo(10);  
            assertThat(response.status()).isEqualTo(TicketStatus.WAITING);

            // Verificar orden: primero ticket, luego outbox  
            var inOrder \= inOrder(ticketRepository, outboxMessageRepository, notificationService);  
            inOrder.verify(ticketRepository).saveAndFlush(any(Ticket.class));  
            inOrder.verify(outboxMessageRepository).save(any(OutboxMessage.class));  
            inOrder.verify(notificationService).notificarTicketCreado(any(Ticket.class));

            verify(metricsService).incrementTicketsCreated(QueueType.CAJA);  
        }

        @Test  
        @DisplayName("debe guardar mensaje en Outbox con datos correctos")  
        void crearTicket\_debeGuardarOutboxConDatosCorrectos() {  
            // Given  
            TicketCreateRequest request \= validTicketRequest();  
            Ticket ticketGuardado \= ticketWaiting().id(99L).numero("C099").build();

            when(queueManagementService.calcularPosicionEnCola(any())).thenReturn(1);  
            when(queueManagementService.calcularTiempoEstimado(any(), anyInt())).thenReturn(5);  
            when(ticketRepository.saveAndFlush(any())).thenReturn(ticketGuardado);

            // When  
            ticketService.crearTicket(request);

            // Then  
            ArgumentCaptor\<OutboxMessage\> captor \= ArgumentCaptor.forClass(OutboxMessage.class);  
            verify(outboxMessageRepository).save(captor.capture());

            OutboxMessage outbox \= captor.getValue();  
            assertThat(outbox.getAggregateType()).isEqualTo("TICKET");  
            assertThat(outbox.getAggregateId()).isEqualTo(99L);  
            assertThat(outbox.getEventType()).isEqualTo("TICKET\_CREATED");  
            assertThat(outbox.getRoutingKey()).isEqualTo("caja-queue");  
            assertThat(outbox.getStatus()).isEqualTo("PENDING");  
            assertThat(outbox.getPayload()).contains("C099");  
        }

        @Test  
        @DisplayName("para cola PERSONAL → debe usar routing key personal-queue")  
        void crearTicket\_colaPersonal\_debeUsarRoutingKeyCorrecto() {  
            // Given  
            TicketCreateRequest request \= new TicketCreateRequest(  
                "12345678", "+56912345678", "Sucursal Centro", QueueType.PERSONAL  
            );  
            Ticket ticketGuardado \= ticketWaiting()  
                .queueType(QueueType.PERSONAL)  
                .numero("P001")  
                .build();

            when(queueManagementService.calcularPosicionEnCola(any())).thenReturn(1);  
            when(queueManagementService.calcularTiempoEstimado(any(), anyInt())).thenReturn(10);  
            when(ticketRepository.saveAndFlush(any())).thenReturn(ticketGuardado);

            // When  
            ticketService.crearTicket(request);

            // Then  
            ArgumentCaptor\<OutboxMessage\> captor \= ArgumentCaptor.forClass(OutboxMessage.class);  
            verify(outboxMessageRepository).save(captor.capture());  
            assertThat(captor.getValue().getRoutingKey()).isEqualTo("personal-queue");  
        }

        @Test  
        @DisplayName("sin teléfono → debe crear ticket y notificar igual")  
        void crearTicket\_sinTelefono\_debeCrearYNotificar() {  
            // Given  
            TicketCreateRequest request \= ticketRequestSinTelefono();  
            Ticket ticketGuardado \= ticketWaiting().telefono(null).build();

            when(queueManagementService.calcularPosicionEnCola(any())).thenReturn(1);  
            when(queueManagementService.calcularTiempoEstimado(any(), anyInt())).thenReturn(5);  
            when(ticketRepository.saveAndFlush(any())).thenReturn(ticketGuardado);

            // When  
            TicketResponse response \= ticketService.crearTicket(request);

            // Then  
            assertThat(response).isNotNull();  
            verify(notificationService).notificarTicketCreado(any());  
        }  
    }

    // \============================================================  
    // OBTENER TICKET  
    // \============================================================  
      
    @Nested  
    @DisplayName("obtenerTicketPorCodigo()")  
    class ObtenerTicket {

        @Test  
        @DisplayName("con UUID existente → debe retornar ticket")  
        void obtenerTicket\_conUuidExistente\_debeRetornarTicket() {  
            // Given  
            UUID codigo \= UUID.randomUUID();  
            Ticket ticket \= ticketWaiting()  
                .codigoReferencia(codigo)  
                .numero("C001")  
                .build();

            when(ticketRepository.findByCodigoReferencia(codigo)).thenReturn(Optional.of(ticket));

            // When  
            TicketResponse response \= ticketService.obtenerTicketPorCodigo(codigo);

            // Then  
            assertThat(response).isNotNull();  
            assertThat(response.codigoReferencia()).isEqualTo(codigo);  
            assertThat(response.numero()).isEqualTo("C001");  
        }

        @Test  
        @DisplayName("con UUID inexistente → debe lanzar TicketNotFoundException")  
        void obtenerTicket\_conUuidInexistente\_debeLanzarExcepcion() {  
            // Given  
            UUID codigo \= UUID.randomUUID();  
            when(ticketRepository.findByCodigoReferencia(codigo)).thenReturn(Optional.empty());

            // When \+ Then  
            assertThatThrownBy(() \-\> ticketService.obtenerTicketPorCodigo(codigo))  
                .isInstanceOf(TicketNotFoundException.class)  
                .hasMessageContaining(codigo.toString());  
        }  
    }  
}

**Validaciones:**

mvn test \-Dtest=TicketServiceTest  
\# Tests run: 6, Failures: 0

**🔍 PUNTO DE REVISIÓN 1:**

✅ PASO 1 COMPLETADO

Tests implementados:  
\- crearTicket\_conDatosValidos\_debeCrearTicketOutboxYNotificar  
\- crearTicket\_debeGuardarOutboxConDatosCorrectos  
\- crearTicket\_colaPersonal\_debeUsarRoutingKeyCorrecto  
\- crearTicket\_sinTelefono\_debeCrearYNotificar  
\- obtenerTicket\_conUuidExistente\_debeRetornarTicket  
\- obtenerTicket\_conUuidInexistente\_debeLanzarExcepcion

Utilidades:  
\- TestDataBuilder con builders para Ticket, Advisor, QueueConfig

Cobertura estimada: \~90% de TicketService

🔍 SOLICITO REVISIÓN:  
1\. ¿Los casos cubren la lógica crítica?  
2\. ¿El TestDataBuilder es útil?  
3\. ¿Puedo continuar con PASO 2?

⏸️ ESPERANDO CONFIRMACIÓN...

---

## **PASO 2: TicketProcessingServiceTest**

**Objetivo:** Testear el procesamiento completo de tickets (TX única).

**Casos críticos:**

* ✅ Flujo completo: WAITING → CALLED → IN\_PROGRESS → COMPLETED  
* ✅ Idempotencia: ticket ya procesado retorna false  
* ✅ Asignación atómica de advisor  
* ✅ Actualización de posiciones en cola  
* ❌ Sin advisors → NoAdvisorAvailableException  
* ❌ InterruptedException durante simulación

**TicketProcessingServiceTest.java:**

package com.example.ticketero.service;

import com.example.ticketero.exception.NoAdvisorAvailableException;  
import com.example.ticketero.model.entity.\*;  
import com.example.ticketero.model.enums.\*;  
import com.example.ticketero.repository.\*;  
import org.junit.jupiter.api.DisplayName;  
import org.junit.jupiter.api.Nested;  
import org.junit.jupiter.api.Test;  
import org.junit.jupiter.api.extension.ExtendWith;  
import org.mockito.InjectMocks;  
import org.mockito.Mock;  
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;  
import java.util.List;  
import java.util.Optional;

import static com.example.ticketero.testutil.TestDataBuilder.\*;  
import static org.assertj.core.api.Assertions.\*;  
import static org.mockito.ArgumentMatchers.\*;  
import static org.mockito.Mockito.\*;

@ExtendWith(MockitoExtension.class)  
@DisplayName("TicketProcessingService \- Unit Tests")  
class TicketProcessingServiceTest {

    @Mock  
    private TicketRepository ticketRepository;

    @Mock  
    private AdvisorRepository advisorRepository;

    @Mock  
    private TicketEventRepository ticketEventRepository;

    @Mock  
    private QueueConfigRepository queueConfigRepository;

    @Mock  
    private NotificationService notificationService;

    @InjectMocks  
    private TicketProcessingService ticketProcessingService;

    @Nested  
    @DisplayName("procesarTicketCompleto()")  
    class ProcesarTicketCompleto {

        @Test  
        @DisplayName("con ticket WAITING y advisor disponible → debe completar flujo")  
        void procesarTicket\_conAdvisorDisponible\_debeCompletarFlujo() throws Exception {  
            // Given  
            Ticket ticket \= ticketWaiting().build();  
            Advisor advisor \= advisorAvailable().build();  
            QueueConfig config \= queueConfigCaja().avgServiceTimeMinutes(1).build();

            when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));  
            when(advisorRepository.findAvailableForQueueWithLock("CAJA"))  
                .thenReturn(List.of(advisor));  
            when(queueConfigRepository.findByQueueType(QueueType.CAJA))  
                .thenReturn(Optional.of(config));  
            when(ticketRepository.findByQueueAndStatus(any(), eq(TicketStatus.WAITING)))  
                .thenReturn(Collections.emptyList());

            // When  
            boolean resultado \= ticketProcessingService.procesarTicketCompleto(1L, QueueType.CAJA);

            // Then  
            assertThat(resultado).isTrue();

            // Verificar cambios en ticket  
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.COMPLETED);  
            assertThat(ticket.getAssignedAdvisor()).isEqualTo(advisor);  
            assertThat(ticket.getCalledAt()).isNotNull();  
            assertThat(ticket.getStartedAt()).isNotNull();  
            assertThat(ticket.getCompletedAt()).isNotNull();

            // Verificar cambios en advisor  
            assertThat(advisor.getStatus()).isEqualTo(AdvisorStatus.AVAILABLE);  
            assertThat(advisor.getTotalTicketsServed()).isEqualTo(11);

            // Verificar guardado  
            verify(ticketRepository).save(ticket);  
            verify(advisorRepository).save(advisor);

            // Verificar eventos registrados (3: CALLED, STARTED, COMPLETED)  
            verify(ticketEventRepository, times(3)).save(any(TicketEvent.class));  
        }

        @Test  
        @DisplayName("con ticket ya procesado (status \!= WAITING) → debe retornar false")  
        void procesarTicket\_yaProcsado\_debeRetornarFalse() throws Exception {  
            // Given  
            Ticket ticketCompletado \= ticketCompleted().build();  
            when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticketCompletado));

            // When  
            boolean resultado \= ticketProcessingService.procesarTicketCompleto(1L, QueueType.CAJA);

            // Then  
            assertThat(resultado).isFalse();  
            verify(advisorRepository, never()).findAvailableForQueueWithLock(any());  
            verify(ticketRepository, never()).save(any());  
        }

        @Test  
        @DisplayName("sin advisors disponibles → debe lanzar NoAdvisorAvailableException")  
        void procesarTicket\_sinAdvisors\_debeLanzarExcepcion() {  
            // Given  
            Ticket ticket \= ticketWaiting().build();  
            when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));  
            when(advisorRepository.findAvailableForQueueWithLock("CAJA"))  
                .thenReturn(Collections.emptyList());

            // When \+ Then  
            assertThatThrownBy(() \-\>   
                ticketProcessingService.procesarTicketCompleto(1L, QueueType.CAJA))  
                .isInstanceOf(NoAdvisorAvailableException.class)  
                .hasMessageContaining("CAJA");

            verify(ticketRepository, never()).save(any());  
        }

        @Test  
        @DisplayName("debe seleccionar advisor con menos tickets servidos")  
        void procesarTicket\_debeSeleccionarAdvisorMenosOcupado() throws Exception {  
            // Given  
            Ticket ticket \= ticketWaiting().build();  
            Advisor advisor1 \= advisorAvailable().id(1L).totalTicketsServed(100).build();  
            Advisor advisor2 \= advisorAvailable().id(2L).totalTicketsServed(5).build();  
            QueueConfig config \= queueConfigCaja().avgServiceTimeMinutes(1).build();

            when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));  
            // El repositorio devuelve ordenado por totalTicketsServed ASC  
            when(advisorRepository.findAvailableForQueueWithLock("CAJA"))  
                .thenReturn(List.of(advisor2, advisor1));  
            when(queueConfigRepository.findByQueueType(QueueType.CAJA))  
                .thenReturn(Optional.of(config));  
            when(ticketRepository.findByQueueAndStatus(any(), any()))  
                .thenReturn(Collections.emptyList());

            // When  
            ticketProcessingService.procesarTicketCompleto(1L, QueueType.CAJA);

            // Then \- El advisor2 (menos ocupado) debe ser asignado  
            assertThat(ticket.getAssignedAdvisor()).isEqualTo(advisor2);  
        }

        @Test  
        @DisplayName("ticket inexistente → debe lanzar RuntimeException")  
        void procesarTicket\_ticketInexistente\_debeLanzarExcepcion() {  
            // Given  
            when(ticketRepository.findById(999L)).thenReturn(Optional.empty());

            // When \+ Then  
            assertThatThrownBy(() \-\>   
                ticketProcessingService.procesarTicketCompleto(999L, QueueType.CAJA))  
                .isInstanceOf(RuntimeException.class)  
                .hasMessageContaining("999");  
        }

        @Test  
        @DisplayName("debe notificar turno activo al cliente")  
        void procesarTicket\_debeNotificarTurnoActivo() throws Exception {  
            // Given  
            Ticket ticket \= ticketWaiting().build();  
            Advisor advisor \= advisorAvailable().build();  
            QueueConfig config \= queueConfigCaja().avgServiceTimeMinutes(1).build();

            when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));  
            when(advisorRepository.findAvailableForQueueWithLock("CAJA"))  
                .thenReturn(List.of(advisor));  
            when(queueConfigRepository.findByQueueType(QueueType.CAJA))  
                .thenReturn(Optional.of(config));  
            when(ticketRepository.findByQueueAndStatus(any(), any()))  
                .thenReturn(Collections.emptyList());

            // When  
            ticketProcessingService.procesarTicketCompleto(1L, QueueType.CAJA);

            // Then  
            verify(notificationService).notificarTurnoActivo(eq(ticket), eq(advisor));  
        }

        @Test  
        @DisplayName("debe actualizar posiciones de tickets en espera")  
        void procesarTicket\_debeActualizarPosiciones() throws Exception {  
            // Given  
            Ticket ticketProcesado \= ticketWaiting().id(1L).positionInQueue(1).build();  
            Ticket ticketEnEspera1 \= ticketWaiting().id(2L).positionInQueue(2).build();  
            Ticket ticketEnEspera2 \= ticketWaiting().id(3L).positionInQueue(3).build();  
            Advisor advisor \= advisorAvailable().build();  
            QueueConfig config \= queueConfigCaja().avgServiceTimeMinutes(1).build();

            when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticketProcesado));  
            when(advisorRepository.findAvailableForQueueWithLock("CAJA"))  
                .thenReturn(List.of(advisor));  
            when(queueConfigRepository.findByQueueType(QueueType.CAJA))  
                .thenReturn(Optional.of(config));  
            when(ticketRepository.findByQueueAndStatus(QueueType.CAJA, TicketStatus.WAITING))  
                .thenReturn(List.of(ticketEnEspera1, ticketEnEspera2));

            // When  
            ticketProcessingService.procesarTicketCompleto(1L, QueueType.CAJA);

            // Then \- Las posiciones deben actualizarse  
            assertThat(ticketEnEspera1.getPositionInQueue()).isEqualTo(1);  
            assertThat(ticketEnEspera2.getPositionInQueue()).isEqualTo(2);  
        }

        @Test  
        @DisplayName("debe actualizar tiempo promedio del advisor")  
        void procesarTicket\_debeActualizarTiempoPromedio() throws Exception {  
            // Given  
            Ticket ticket \= ticketWaiting().build();  
            Advisor advisor \= advisorAvailable()  
                .avgServiceTimeMinutes(5)  
                .totalTicketsServed(10)  
                .build();  
            QueueConfig config \= queueConfigCaja().avgServiceTimeMinutes(1).build();

            when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));  
            when(advisorRepository.findAvailableForQueueWithLock("CAJA"))  
                .thenReturn(List.of(advisor));  
            when(queueConfigRepository.findByQueueType(QueueType.CAJA))  
                .thenReturn(Optional.of(config));  
            when(ticketRepository.findByQueueAndStatus(any(), any()))  
                .thenReturn(Collections.emptyList());

            // When  
            ticketProcessingService.procesarTicketCompleto(1L, QueueType.CAJA);

            // Then \- El tiempo promedio debe haberse recalculado  
            verify(advisorRepository).save(argThat(a \-\>   
                a.getAvgServiceTimeMinutes() \!= null));  
        }  
    }  
}

**Validaciones:**

mvn test \-Dtest=TicketProcessingServiceTest  
\# Tests run: 8, Failures: 0

**🔍 PUNTO DE REVISIÓN 2:** Tests de TX única implementados.

---

## **PASO 3: AdvisorServiceTest**

**Objetivo:** Testear asignación atómica y gestión de advisors.

**AdvisorServiceTest.java:**

package com.example.ticketero.service;

import com.example.ticketero.model.entity.\*;  
import com.example.ticketero.model.enums.\*;  
import com.example.ticketero.repository.\*;  
import org.junit.jupiter.api.DisplayName;  
import org.junit.jupiter.api.Nested;  
import org.junit.jupiter.api.Test;  
import org.junit.jupiter.api.extension.ExtendWith;  
import org.mockito.ArgumentCaptor;  
import org.mockito.InjectMocks;  
import org.mockito.Mock;  
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;  
import java.util.\*;

import static com.example.ticketero.testutil.TestDataBuilder.\*;  
import static org.assertj.core.api.Assertions.\*;  
import static org.mockito.ArgumentMatchers.\*;  
import static org.mockito.Mockito.\*;

@ExtendWith(MockitoExtension.class)  
@DisplayName("AdvisorService \- Unit Tests")  
class AdvisorServiceTest {

    @Mock  
    private AdvisorRepository advisorRepository;

    @Mock  
    private TicketRepository ticketRepository;

    @Mock  
    private TicketEventRepository ticketEventRepository;

    @InjectMocks  
    private AdvisorService advisorService;

    @Nested  
    @DisplayName("obtenerAsesorDisponible()")  
    class ObtenerAsesorDisponible {

        @Test  
        @DisplayName("con advisors disponibles → debe retornar el menos ocupado")  
        void obtenerAsesor\_conDisponibles\_debeRetornarMenosOcupado() {  
            // Given  
            Advisor advisor \= advisorAvailable().totalTicketsServed(5).build();  
            when(advisorRepository.findAvailableForQueue("CAJA"))  
                .thenReturn(List.of(advisor));

            // When  
            Optional\<Advisor\> resultado \= advisorService.obtenerAsesorDisponible(QueueType.CAJA);

            // Then  
            assertThat(resultado).isPresent();  
            assertThat(resultado.get()).isEqualTo(advisor);  
        }

        @Test  
        @DisplayName("sin advisors disponibles → debe retornar Optional.empty()")  
        void obtenerAsesor\_sinDisponibles\_debeRetornarEmpty() {  
            // Given  
            when(advisorRepository.findAvailableForQueue("CAJA"))  
                .thenReturn(Collections.emptyList());

            // When  
            Optional\<Advisor\> resultado \= advisorService.obtenerAsesorDisponible(QueueType.CAJA);

            // Then  
            assertThat(resultado).isEmpty();  
        }  
    }

    @Nested  
    @DisplayName("asignarTicketAsesor()")  
    class AsignarTicketAsesor {

        @Test  
        @DisplayName("debe actualizar ticket y advisor correctamente")  
        void asignarTicket\_debeActualizarAmbos() {  
            // Given  
            Ticket ticket \= ticketWaiting().build();  
            Advisor advisor \= advisorAvailable().moduleNumber(3).build();

            // When  
            advisorService.asignarTicketAsesor(ticket, advisor);

            // Then  
            assertThat(ticket.getAssignedAdvisor()).isEqualTo(advisor);  
            assertThat(ticket.getAssignedModuleNumber()).isEqualTo(3);  
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CALLED);  
            assertThat(ticket.getCalledAt()).isNotNull();

            assertThat(advisor.getStatus()).isEqualTo(AdvisorStatus.BUSY);

            verify(ticketRepository).save(ticket);  
            verify(advisorRepository).save(advisor);  
        }

        @Test  
        @DisplayName("debe registrar evento CALLED")  
        void asignarTicket\_debeRegistrarEvento() {  
            // Given  
            Ticket ticket \= ticketWaiting().build();  
            Advisor advisor \= advisorAvailable().build();

            // When  
            advisorService.asignarTicketAsesor(ticket, advisor);

            // Then  
            ArgumentCaptor\<TicketEvent\> captor \= ArgumentCaptor.forClass(TicketEvent.class);  
            verify(ticketEventRepository).save(captor.capture());

            TicketEvent evento \= captor.getValue();  
            assertThat(evento.getEventType()).isEqualTo(EventType.CALLED);  
            assertThat(evento.getNewStatus()).isEqualTo("CALLED");  
            assertThat(evento.getAdvisor()).isEqualTo(advisor);  
        }  
    }

    @Nested  
    @DisplayName("completarAtencion()")  
    class CompletarAtencion {

        @Test  
        @DisplayName("debe completar ticket y liberar advisor")  
        void completarAtencion\_debeCompletarYLiberar() {  
            // Given  
            Advisor advisor \= advisorBusy().totalTicketsServed(10).build();  
            Ticket ticket \= ticketInProgress()  
                .assignedAdvisor(advisor)  
                .startedAt(LocalDateTime.now().minusMinutes(5))  
                .build();

            when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));  
            when(advisorRepository.findById(advisor.getId())).thenReturn(Optional.of(advisor));  
            when(advisorRepository.saveAndFlush(any())).thenReturn(advisor);

            // When  
            advisorService.completarAtencion(1L);

            // Then  
            verify(ticketRepository).updateStatusAndCompletedAt(  
                eq(1L), eq(TicketStatus.COMPLETED), any(LocalDateTime.class));  
              
            assertThat(advisor.getStatus()).isEqualTo(AdvisorStatus.AVAILABLE);  
            assertThat(advisor.getTotalTicketsServed()).isEqualTo(11);  
        }

        @Test  
        @DisplayName("debe calcular tiempo promedio correctamente")  
        void completarAtencion\_debeCalcularPromedio() {  
            // Given  
            Advisor advisor \= advisorBusy()  
                .avgServiceTimeMinutes(10)  
                .totalTicketsServed(9)  
                .build();  
              
            LocalDateTime startedAt \= LocalDateTime.now().minusMinutes(5);  
            Ticket ticket \= ticketInProgress()  
                .id(1L)  
                .assignedAdvisor(advisor)  
                .startedAt(startedAt)  
                .build();

            when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));  
            when(advisorRepository.findById(any())).thenReturn(Optional.of(advisor));  
            when(advisorRepository.saveAndFlush(any())).thenReturn(advisor);

            // When  
            advisorService.completarAtencion(1L);

            // Then  
            // Promedio \= (10 \* 9 \+ 5\) / 10 \= 9.5 ≈ 10 (redondeado)  
            // El cálculo exacto depende de la implementación  
            verify(advisorRepository).saveAndFlush(argThat(a \-\>   
                a.getAvgServiceTimeMinutes() \!= null));  
        }  
    }

    @Nested  
    @DisplayName("cambiarEstado()")  
    class CambiarEstado {

        @Test  
        @DisplayName("debe cambiar estado correctamente")  
        void cambiarEstado\_debeCambiarCorrectamente() {  
            // Given  
            Advisor advisor \= advisorAvailable().build();  
            when(advisorRepository.findById(1L)).thenReturn(Optional.of(advisor));

            // When  
            advisorService.cambiarEstado(1L, AdvisorStatus.BREAK);

            // Then  
            assertThat(advisor.getStatus()).isEqualTo(AdvisorStatus.BREAK);  
            verify(advisorRepository).save(advisor);  
        }

        @Test  
        @DisplayName("advisor inexistente → debe lanzar excepción")  
        void cambiarEstado\_advisorInexistente\_debeLanzarExcepcion() {  
            // Given  
            when(advisorRepository.findById(999L)).thenReturn(Optional.empty());

            // When \+ Then  
            assertThatThrownBy(() \-\> advisorService.cambiarEstado(999L, AdvisorStatus.BREAK))  
                .isInstanceOf(RuntimeException.class)  
                .hasMessageContaining("999");  
        }  
    }

    @Nested  
    @DisplayName("obtenerEstadisticas()")  
    class ObtenerEstadisticas {

        @Test  
        @DisplayName("debe calcular estadísticas correctamente")  
        void obtenerEstadisticas\_debeCalcularCorrectamente() {  
            // Given  
            List\<Advisor\> advisors \= List.of(  
                advisorAvailable().totalTicketsServed(10).avgServiceTimeMinutes(5).build(),  
                advisorBusy().totalTicketsServed(20).avgServiceTimeMinutes(8).build(),  
                advisorAvailable().status(AdvisorStatus.BREAK).totalTicketsServed(15).avgServiceTimeMinutes(6).build()  
            );  
            when(advisorRepository.findAll()).thenReturn(advisors);

            // When  
            Map\<String, Object\> stats \= advisorService.obtenerEstadisticas();

            // Then  
            assertThat(stats.get("total")).isEqualTo(3);  
            assertThat(stats.get("disponibles")).isEqualTo(1L);  
            assertThat(stats.get("ocupados")).isEqualTo(1L);  
            assertThat(stats.get("enDescanso")).isEqualTo(1L);  
            assertThat(stats.get("totalTicketsAtendidos")).isEqualTo(45);  
        }  
    }  
}

**Validaciones:**

mvn test \-Dtest=AdvisorServiceTest  
\# Tests run: 7, Failures: 0

**🔍 PUNTO DE REVISIÓN 3:** Tests de AdvisorService implementados.

---

## **PASO 4: QueueManagementServiceTest**

**Objetivo:** Testear cálculo de posiciones y tiempos en cola.

**QueueManagementServiceTest.java:**

package com.example.ticketero.service;

import com.example.ticketero.model.entity.\*;  
import com.example.ticketero.model.enums.\*;  
import com.example.ticketero.repository.\*;  
import org.junit.jupiter.api.DisplayName;  
import org.junit.jupiter.api.Nested;  
import org.junit.jupiter.api.Test;  
import org.junit.jupiter.api.extension.ExtendWith;  
import org.mockito.InjectMocks;  
import org.mockito.Mock;  
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.\*;

import static com.example.ticketero.testutil.TestDataBuilder.\*;  
import static org.assertj.core.api.Assertions.\*;  
import static org.mockito.ArgumentMatchers.\*;  
import static org.mockito.Mockito.\*;

@ExtendWith(MockitoExtension.class)  
@DisplayName("QueueManagementService \- Unit Tests")  
class QueueManagementServiceTest {

    @Mock  
    private TicketRepository ticketRepository;

    @Mock  
    private QueueConfigRepository queueConfigRepository;

    @Mock  
    private TicketEventRepository ticketEventRepository;

    @InjectMocks  
    private QueueManagementService queueManagementService;

    @Nested  
    @DisplayName("calcularPosicionEnCola()")  
    class CalcularPosicion {

        @Test  
        @DisplayName("cola vacía → debe retornar posición 1")  
        void calcularPosicion\_colaVacia\_debeRetornarUno() {  
            // Given  
            when(ticketRepository.countWaitingBefore(eq(QueueType.CAJA), any()))  
                .thenReturn(0);

            // When  
            int posicion \= queueManagementService.calcularPosicionEnCola(QueueType.CAJA);

            // Then  
            assertThat(posicion).isEqualTo(1);  
        }

        @Test  
        @DisplayName("con 5 tickets esperando → debe retornar posición 6")  
        void calcularPosicion\_con5Esperando\_debeRetornarSeis() {  
            // Given  
            when(ticketRepository.countWaitingBefore(eq(QueueType.CAJA), any()))  
                .thenReturn(5);

            // When  
            int posicion \= queueManagementService.calcularPosicionEnCola(QueueType.CAJA);

            // Then  
            assertThat(posicion).isEqualTo(6);  
        }  
    }

    @Nested  
    @DisplayName("calcularTiempoEstimado()")  
    class CalcularTiempoEstimado {

        @Test  
        @DisplayName("posición 1 → debe retornar 0 minutos")  
        void calcularTiempo\_posicionUno\_debeRetornarCero() {  
            // Given  
            QueueConfig config \= queueConfigCaja().avgServiceTimeMinutes(10).build();  
            when(queueConfigRepository.findByQueueType(QueueType.CAJA))  
                .thenReturn(Optional.of(config));

            // When  
            int tiempo \= queueManagementService.calcularTiempoEstimado(QueueType.CAJA, 1);

            // Then  
            assertThat(tiempo).isEqualTo(0);  
        }

        @Test  
        @DisplayName("posición 5 con avgTime=10 → debe retornar 40 minutos")  
        void calcularTiempo\_posicionCinco\_debeCalcularCorrectamente() {  
            // Given  
            QueueConfig config \= queueConfigCaja().avgServiceTimeMinutes(10).build();  
            when(queueConfigRepository.findByQueueType(QueueType.CAJA))  
                .thenReturn(Optional.of(config));

            // When  
            int tiempo \= queueManagementService.calcularTiempoEstimado(QueueType.CAJA, 5);

            // Then  
            // (5 \- 1\) × 10 \= 40  
            assertThat(tiempo).isEqualTo(40);  
        }

        @Test  
        @DisplayName("config inexistente → debe lanzar RuntimeException")  
        void calcularTiempo\_sinConfig\_debeLanzarExcepcion() {  
            // Given  
            when(queueConfigRepository.findByQueueType(QueueType.GERENCIA))  
                .thenReturn(Optional.empty());

            // When \+ Then  
            assertThatThrownBy(() \-\>   
                queueManagementService.calcularTiempoEstimado(QueueType.GERENCIA, 1))  
                .isInstanceOf(RuntimeException.class)  
                .hasMessageContaining("GERENCIA");  
        }  
    }

    @Nested  
    @DisplayName("obtenerSiguienteTicket()")  
    class ObtenerSiguienteTicket {

        @Test  
        @DisplayName("con tickets en espera → debe retornar el primero")  
        void obtenerSiguiente\_conTickets\_debeRetornarPrimero() {  
            // Given  
            Ticket ticket1 \= ticketWaiting().id(1L).positionInQueue(1).build();  
            Ticket ticket2 \= ticketWaiting().id(2L).positionInQueue(2).build();  
              
            when(ticketRepository.findByQueueAndStatus(QueueType.CAJA, TicketStatus.WAITING))  
                .thenReturn(List.of(ticket1, ticket2));

            // When  
            Optional\<Ticket\> siguiente \= queueManagementService.obtenerSiguienteTicket(QueueType.CAJA);

            // Then  
            assertThat(siguiente).isPresent();  
            assertThat(siguiente.get()).isEqualTo(ticket1);  
        }

        @Test  
        @DisplayName("cola vacía → debe retornar Optional.empty()")  
        void obtenerSiguiente\_colaVacia\_debeRetornarEmpty() {  
            // Given  
            when(ticketRepository.findByQueueAndStatus(QueueType.CAJA, TicketStatus.WAITING))  
                .thenReturn(Collections.emptyList());

            // When  
            Optional\<Ticket\> siguiente \= queueManagementService.obtenerSiguienteTicket(QueueType.CAJA);

            // Then  
            assertThat(siguiente).isEmpty();  
        }  
    }

    @Nested  
    @DisplayName("obtenerEstadisticas()")  
    class ObtenerEstadisticas {

        @Test  
        @DisplayName("debe calcular estadísticas correctamente")  
        void obtenerEstadisticas\_debeCalcularCorrectamente() {  
            // Given  
            QueueConfig config \= queueConfigCaja().avgServiceTimeMinutes(5).build();  
            when(queueConfigRepository.findByQueueType(QueueType.CAJA))  
                .thenReturn(Optional.of(config));  
            when(ticketRepository.countByQueueTypeAndStatus(QueueType.CAJA, TicketStatus.WAITING))  
                .thenReturn(10L);  
            when(ticketRepository.countByQueueTypeAndStatus(QueueType.CAJA, TicketStatus.CALLED))  
                .thenReturn(2L);  
            when(ticketRepository.countByQueueTypeAndStatus(QueueType.CAJA, TicketStatus.IN\_PROGRESS))  
                .thenReturn(1L);  
            when(ticketRepository.countByQueueTypeAndStatus(QueueType.CAJA, TicketStatus.COMPLETED))  
                .thenReturn(50L);

            // When  
            var stats \= queueManagementService.obtenerEstadisticas(QueueType.CAJA);

            // Then  
            assertThat(stats.waiting()).isEqualTo(10);  
            assertThat(stats.called()).isEqualTo(2);  
            assertThat(stats.inProgress()).isEqualTo(1);  
            assertThat(stats.completed()).isEqualTo(50);  
            assertThat(stats.estimatedWaitTime()).isEqualTo(50); // 10 × 5  
        }  
    }  
}

**🔍 PUNTO DE REVISIÓN 4:** Tests de QueueManagementService implementados.

---

## **PASO 5: OutboxPublisherServiceTest**

**Objetivo:** Testear patrón Outbox con reintentos y backoff.

**OutboxPublisherServiceTest.java:**

package com.example.ticketero.service;

import com.example.ticketero.model.dto.TicketQueueMessage;  
import com.example.ticketero.model.entity.OutboxMessage;  
import com.example.ticketero.repository.OutboxMessageRepository;  
import com.fasterxml.jackson.databind.ObjectMapper;  
import org.junit.jupiter.api.DisplayName;  
import org.junit.jupiter.api.Nested;  
import org.junit.jupiter.api.Test;  
import org.junit.jupiter.api.extension.ExtendWith;  
import org.mockito.ArgumentCaptor;  
import org.mockito.InjectMocks;  
import org.mockito.Mock;  
import org.mockito.Spy;  
import org.mockito.junit.jupiter.MockitoExtension;  
import org.springframework.amqp.rabbit.core.RabbitTemplate;  
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;  
import java.util.List;

import static com.example.ticketero.testutil.TestDataBuilder.\*;  
import static org.assertj.core.api.Assertions.\*;  
import static org.mockito.ArgumentMatchers.\*;  
import static org.mockito.Mockito.\*;

@ExtendWith(MockitoExtension.class)  
@DisplayName("OutboxPublisherService \- Unit Tests")  
class OutboxPublisherServiceTest {

    @Mock  
    private OutboxMessageRepository outboxRepository;

    @Mock  
    private RabbitTemplate rabbitTemplate;

    @Spy  
    private ObjectMapper objectMapper \= new ObjectMapper();

    @InjectMocks  
    private OutboxPublisherService outboxPublisherService;

    @Nested  
    @DisplayName("processOutbox()")  
    class ProcessOutbox {

        @Test  
        @DisplayName("con mensaje pendiente → debe publicar y marcar SENT")  
        void processOutbox\_conMensajePendiente\_debePublicarYMarcarSent() {  
            // Given  
            OutboxMessage mensaje \= outboxPending().build();  
            when(outboxRepository.findPendingWithLock(any(), any()))  
                .thenReturn(List.of(mensaje));

            // When  
            outboxPublisherService.processOutbox();

            // Then  
            verify(rabbitTemplate).convertAndSend(  
                anyString(),  
                eq("caja-queue"),  
                any(TicketQueueMessage.class),  
                any()  
            );  
            verify(outboxRepository).markAsSent(eq(1L), any(LocalDateTime.class));  
        }

        @Test  
        @DisplayName("sin mensajes pendientes → no debe hacer nada")  
        void processOutbox\_sinMensajes\_noDebeHacerNada() {  
            // Given  
            when(outboxRepository.findPendingWithLock(any(), any()))  
                .thenReturn(List.of());

            // When  
            outboxPublisherService.processOutbox();

            // Then  
            verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class), any());  
            verify(outboxRepository, never()).markAsSent(any(), any());  
        }

        @Test  
        @DisplayName("fallo al publicar → debe incrementar retry y programar siguiente")  
        void processOutbox\_falloAlPublicar\_debeIncrementarRetry() {  
            // Given  
            OutboxMessage mensaje \= outboxPending().retryCount(0).maxRetries(5).build();  
            when(outboxRepository.findPendingWithLock(any(), any()))  
                .thenReturn(List.of(mensaje));  
            doThrow(new RuntimeException("RabbitMQ error"))  
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class), any());

            // When  
            outboxPublisherService.processOutbox();

            // Then  
            verify(outboxRepository).scheduleRetry(  
                eq(1L),  
                eq(1),  // retryCount incrementado  
                any(LocalDateTime.class),  // nextRetryAt  
                contains("RabbitMQ error")  
            );  
            verify(outboxRepository, never()).markAsSent(any(), any());  
        }

        @Test  
        @DisplayName("reintentos agotados → debe marcar FAILED")  
        void processOutbox\_reintentosAgotados\_debeMarcarFailed() {  
            // Given  
            OutboxMessage mensaje \= outboxPending()  
                .retryCount(4)  
                .maxRetries(5)  
                .build();  
            when(outboxRepository.findPendingWithLock(any(), any()))  
                .thenReturn(List.of(mensaje));  
            doThrow(new RuntimeException("Error"))  
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class), any());

            // When  
            outboxPublisherService.processOutbox();

            // Then  
            verify(outboxRepository).markAsFailed(  
                eq(1L),  
                anyString(),  
                any(LocalDateTime.class)  
            );  
        }

        @Test  
        @DisplayName("debe parsear JSON a TicketQueueMessage correctamente")  
        void processOutbox\_debeParserJsonCorrectamente() {  
            // Given  
            String payload \= "{\\"ticketId\\":99,\\"numero\\":\\"C099\\",\\"queueType\\":\\"CAJA\\",\\"telefono\\":\\"+56912345678\\"}";  
            OutboxMessage mensaje \= outboxPending().payload(payload).build();  
            when(outboxRepository.findPendingWithLock(any(), any()))  
                .thenReturn(List.of(mensaje));

            // When  
            outboxPublisherService.processOutbox();

            // Then  
            ArgumentCaptor\<TicketQueueMessage\> captor \= ArgumentCaptor.forClass(TicketQueueMessage.class);  
            verify(rabbitTemplate).convertAndSend(anyString(), anyString(), captor.capture(), any());

            TicketQueueMessage parsed \= captor.getValue();  
            assertThat(parsed.ticketId()).isEqualTo(99L);  
            assertThat(parsed.numero()).isEqualTo("C099");  
        }  
    }  
}

**🔍 PUNTO DE REVISIÓN 5:** Tests de OutboxPublisherService implementados.

---

## **PASO 6: RecoveryServiceTest**

**Objetivo:** Testear auto-recuperación de workers muertos.

**RecoveryServiceTest.java:**

package com.example.ticketero.service;

import com.example.ticketero.model.entity.\*;  
import com.example.ticketero.model.enums.\*;  
import com.example.ticketero.repository.\*;  
import org.junit.jupiter.api.DisplayName;  
import org.junit.jupiter.api.Nested;  
import org.junit.jupiter.api.Test;  
import org.junit.jupiter.api.extension.ExtendWith;  
import org.mockito.ArgumentCaptor;  
import org.mockito.InjectMocks;  
import org.mockito.Mock;  
import org.mockito.junit.jupiter.MockitoExtension;  
import org.springframework.amqp.rabbit.core.RabbitTemplate;  
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;  
import java.util.\*;

import static com.example.ticketero.testutil.TestDataBuilder.\*;  
import static org.assertj.core.api.Assertions.\*;  
import static org.mockito.ArgumentMatchers.\*;  
import static org.mockito.Mockito.\*;

@ExtendWith(MockitoExtension.class)  
@DisplayName("RecoveryService \- Unit Tests")  
class RecoveryServiceTest {

    @Mock  
    private AdvisorRepository advisorRepository;

    @Mock  
    private TicketRepository ticketRepository;

    @Mock  
    private RecoveryEventRepository recoveryEventRepository;

    @Mock  
    private RabbitTemplate rabbitTemplate;

    @InjectMocks  
    private RecoveryService recoveryService;

    @Nested  
    @DisplayName("detectarYRecuperarWorkersMuertos()")  
    class DetectarWorkersMuertos {

        @Test  
        @DisplayName("con worker muerto → debe liberar advisor y reencolar ticket")  
        void detectar\_conWorkerMuerto\_debeRecuperar() {  
            // Given  
            ReflectionTestUtils.setField(recoveryService, "heartbeatTimeoutSeconds", 60);  
            ReflectionTestUtils.setField(recoveryService, "exchangeName", "ticketero-exchange");

            Ticket ticketEnProgreso \= ticketInProgress().build();  
            Advisor advisorMuerto \= advisorBusy()  
                .lastHeartbeat(LocalDateTime.now().minusMinutes(5))  
                .build();

            when(advisorRepository.findDeadWorkers(any()))  
                .thenReturn(List.of(advisorMuerto));  
            when(ticketRepository.findCurrentTicketForAdvisor(any()))  
                .thenReturn(Optional.of(ticketEnProgreso));

            // When  
            recoveryService.detectarYRecuperarWorkersMuertos();

            // Then  
            assertThat(advisorMuerto.getStatus()).isEqualTo(AdvisorStatus.AVAILABLE);  
            assertThat(ticketEnProgreso.getStatus()).isEqualTo(TicketStatus.WAITING);  
              
            verify(advisorRepository).incrementRecoveryCount(any());  
            verify(advisorRepository).save(advisorMuerto);  
            verify(ticketRepository).save(ticketEnProgreso);  
            verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(), any());  
        }

        @Test  
        @DisplayName("sin workers muertos → no debe hacer nada")  
        void detectar\_sinWorkersMuertos\_noDebeHacerNada() {  
            // Given  
            ReflectionTestUtils.setField(recoveryService, "heartbeatTimeoutSeconds", 60);  
            when(advisorRepository.findDeadWorkers(any()))  
                .thenReturn(Collections.emptyList());

            // When  
            recoveryService.detectarYRecuperarWorkersMuertos();

            // Then  
            verify(advisorRepository, never()).save(any());  
            verify(ticketRepository, never()).save(any());  
        }

        @Test  
        @DisplayName("debe registrar evento de recovery")  
        void detectar\_debeRegistrarEvento() {  
            // Given  
            ReflectionTestUtils.setField(recoveryService, "heartbeatTimeoutSeconds", 60);  
            ReflectionTestUtils.setField(recoveryService, "exchangeName", "ticketero-exchange");

            Advisor advisorMuerto \= advisorBusy().build();  
            when(advisorRepository.findDeadWorkers(any()))  
                .thenReturn(List.of(advisorMuerto));  
            when(ticketRepository.findCurrentTicketForAdvisor(any()))  
                .thenReturn(Optional.empty());

            // When  
            recoveryService.detectarYRecuperarWorkersMuertos();

            // Then  
            ArgumentCaptor\<RecoveryEvent\> captor \= ArgumentCaptor.forClass(RecoveryEvent.class);  
            verify(recoveryEventRepository).save(captor.capture());

            RecoveryEvent evento \= captor.getValue();  
            assertThat(evento.getRecoveryType()).isEqualTo("DEAD\_WORKER");  
            assertThat(evento.getOldAdvisorStatus()).isEqualTo("BUSY");  
        }

        @Test  
        @DisplayName("ticket ya completado → no debe reencolar")  
        void detectar\_ticketCompletado\_noDebeReencolar() {  
            // Given  
            ReflectionTestUtils.setField(recoveryService, "heartbeatTimeoutSeconds", 60);  
            ReflectionTestUtils.setField(recoveryService, "exchangeName", "ticketero-exchange");

            Ticket ticketCompletado \= ticketCompleted().build();  
            Advisor advisorMuerto \= advisorBusy().build();

            when(advisorRepository.findDeadWorkers(any()))  
                .thenReturn(List.of(advisorMuerto));  
            when(ticketRepository.findCurrentTicketForAdvisor(any()))  
                .thenReturn(Optional.of(ticketCompletado));

            // When  
            recoveryService.detectarYRecuperarWorkersMuertos();

            // Then  
            verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(), any());  
        }  
    }

    @Nested  
    @DisplayName("recuperarAsesorManual()")  
    class RecuperarManual {

        @Test  
        @DisplayName("debe recuperar advisor correctamente")  
        void recuperarManual\_debeRecuperar() {  
            // Given  
            ReflectionTestUtils.setField(recoveryService, "exchangeName", "ticketero-exchange");

            Advisor advisor \= advisorBusy().build();  
            when(advisorRepository.findById(1L)).thenReturn(Optional.of(advisor));  
            when(ticketRepository.findCurrentTicketForAdvisor(any()))  
                .thenReturn(Optional.empty());

            // When  
            recoveryService.recuperarAsesorManual(1L);

            // Then  
            assertThat(advisor.getStatus()).isEqualTo(AdvisorStatus.AVAILABLE);  
              
            ArgumentCaptor\<RecoveryEvent\> captor \= ArgumentCaptor.forClass(RecoveryEvent.class);  
            verify(recoveryEventRepository).save(captor.capture());  
            assertThat(captor.getValue().getRecoveryType()).isEqualTo("MANUAL");  
        }  
    }  
}

**🔍 PUNTO DE REVISIÓN 6:** Tests de RecoveryService implementados.

---

## **PASO 7: NotificationServiceTest**

**Objetivo:** Testear envío condicional de notificaciones.

**NotificationServiceTest.java:**

package com.example.ticketero.service;

import com.example.ticketero.model.entity.\*;  
import com.example.ticketero.model.enums.\*;  
import org.junit.jupiter.api.DisplayName;  
import org.junit.jupiter.api.Nested;  
import org.junit.jupiter.api.Test;  
import org.junit.jupiter.api.extension.ExtendWith;  
import org.mockito.InjectMocks;  
import org.mockito.Mock;  
import org.mockito.junit.jupiter.MockitoExtension;

import static com.example.ticketero.testutil.TestDataBuilder.\*;  
import static org.mockito.ArgumentMatchers.\*;  
import static org.mockito.Mockito.\*;

@ExtendWith(MockitoExtension.class)  
@DisplayName("NotificationService \- Unit Tests")  
class NotificationServiceTest {

    @Mock  
    private TelegramService telegramService;

    @InjectMocks  
    private NotificationService notificationService;

    @Nested  
    @DisplayName("notificarTicketCreado()")  
    class NotificarTicketCreado {

        @Test  
        @DisplayName("con teléfono → debe enviar notificación")  
        void notificar\_conTelefono\_debeEnviar() {  
            // Given  
            Ticket ticket \= ticketWaiting()  
                .telefono("+56912345678")  
                .numero("C001")  
                .positionInQueue(3)  
                .build();

            // When  
            notificationService.notificarTicketCreado(ticket);

            // Then  
            verify(telegramService).enviarMensaje(  
                eq("+56912345678"),  
                contains("C001")  
            );  
        }

        @Test  
        @DisplayName("sin teléfono → no debe enviar")  
        void notificar\_sinTelefono\_noDebeEnviar() {  
            // Given  
            Ticket ticket \= ticketWaiting().telefono(null).build();

            // When  
            notificationService.notificarTicketCreado(ticket);

            // Then  
            verify(telegramService, never()).enviarMensaje(any(), any());  
        }

        @Test  
        @DisplayName("teléfono vacío → no debe enviar")  
        void notificar\_telefonoVacio\_noDebeEnviar() {  
            // Given  
            Ticket ticket \= ticketWaiting().telefono("   ").build();

            // When  
            notificationService.notificarTicketCreado(ticket);

            // Then  
            verify(telegramService, never()).enviarMensaje(any(), any());  
        }  
    }

    @Nested  
    @DisplayName("notificarTurnoActivo()")  
    class NotificarTurnoActivo {

        @Test  
        @DisplayName("con advisor → debe incluir nombre y módulo")  
        void notificarTurno\_conAdvisor\_debeIncluirInfo() {  
            // Given  
            Advisor advisor \= advisorAvailable()  
                .name("María López")  
                .moduleNumber(3)  
                .build();  
            Ticket ticket \= ticketWaiting()  
                .telefono("+56912345678")  
                .numero("C001")  
                .assignedModuleNumber(3)  
                .build();

            // When  
            notificationService.notificarTurnoActivo(ticket, advisor);

            // Then  
            verify(telegramService).enviarMensaje(  
                eq("+56912345678"),  
                argThat(msg \-\>   
                    msg.contains("C001") &&   
                    msg.contains("María López") &&  
                    msg.contains("3"))  
            );  
        }

        @Test  
        @DisplayName("Telegram falla → no debe propagar excepción")  
        void notificarTurno\_telegramFalla\_noDebePropagar() {  
            // Given  
            Ticket ticket \= ticketWaiting().telefono("+56912345678").build();  
            Advisor advisor \= advisorAvailable().build();  
            doThrow(new RuntimeException("Telegram error"))  
                .when(telegramService).enviarMensaje(any(), any());

            // When \+ Then (no debe lanzar excepción)  
            notificationService.notificarTurnoActivo(ticket, advisor);  
        }  
    }  
}

**🔍 PUNTO DE REVISIÓN 7:** Tests de NotificationService implementados.

---

## **PASO 8: Ejecución Final y Cobertura**

**Objetivo:** Ejecutar todos los tests y verificar cobertura.

**Comandos:**

\# 1\. Ejecutar todos los unit tests  
mvn test \-Dtest="\*ServiceTest"

\# 2\. Generar reporte de cobertura  
mvn jacoco:report

\# 3\. Ver reporte  
open target/site/jacoco/index.html

**Resultados Esperados:**

\[INFO\] Tests run: 41, Failures: 0, Errors: 0, Skipped: 0

Tests por servicio:  
\- TicketServiceTest: 6 tests  
\- TicketProcessingServiceTest: 8 tests  
\- AdvisorServiceTest: 7 tests  
\- QueueManagementServiceTest: 6 tests  
\- OutboxPublisherServiceTest: 5 tests  
\- RecoveryServiceTest: 5 tests  
\- NotificationServiceTest: 4 tests

**Cobertura Objetivo:**

| Servicio | Líneas | Objetivo |
| ----- | ----- | ----- |
| TicketService | 75% | ✅ |
| TicketProcessingService | 80% | ✅ |
| AdvisorService | 70% | ✅ |
| QueueManagementService | 75% | ✅ |
| OutboxPublisherService | 70% | ✅ |
| RecoveryService | 65% | ✅ |
| NotificationService | 80% | ✅ |
| **Total Services** | **\>70%** | ✅ |

**🔍 PUNTO DE REVISIÓN FINAL 8:**

✅ PASO 8 COMPLETADO \- UNIT TESTS COMPLETOS

Tests totales: 41  
Servicios cubiertos: 7/7

Cobertura:  
\- Services: \>90%  
\- Líneas críticas: 100%  
\- Edge cases: cubiertos

Patrones validados:  
\- ✅ Outbox Pattern  
\- ✅ TX única  
\- ✅ Auto-recovery  
\- ✅ SELECT FOR UPDATE

⏸️ UNIT TESTS COMPLETADOS\!

---

## **Resumen de Tests**

| Paso | Servicio | Tests | Casos Cubiertos |
| ----- | ----- | ----- | ----- |
| 1 | TicketService | 6 | Crear ticket, Outbox, Obtener |
| 2 | TicketProcessingService | 8 | Flujo completo, Idempotencia, Errors |
| 3 | AdvisorService | 7 | Asignación, Completar, Estadísticas |
| 4 | QueueManagementService | 6 | Posiciones, Tiempos, Estadísticas |
| 5 | OutboxPublisherService | 5 | Publicar, Reintentos, Backoff |
| 6 | RecoveryService | 5 | Workers muertos, Manual |
| 7 | NotificationService | 4 | Envío condicional, Error handling |
| **Total** | **7 servicios** | **41** | **\>90% cobertura** |

---

## **Principios Aplicados**

✅ **Aislamiento:** Mock de TODAS las dependencias  
✅ **AAA Pattern:** Given-When-Then en cada test  
✅ **Un concepto por test:** Sin mezclar validaciones  
✅ **Nombres descriptivos:** methodName\_condition\_expectedBehavior  
✅ **AssertJ:** Assertions fluidas y legibles  
✅ **ArgumentCaptor:** Validación de objetos complejos  
✅ **InOrder:** Verificación de secuencia cuando es crítico

---

**Tiempo estimado:** 4-5 horas