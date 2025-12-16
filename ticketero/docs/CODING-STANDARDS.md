# 📐 Estándares de Código - Sistema Ticketero

**Versión:** 2.0
**Última actualización:** 25 de noviembre de 2025

---

## ⚠️ FILOSOFÍA: SIMPLICIDAD

### PRINCIPIOS
- ✅ Código directo y simple
- ✅ Soluciona el problema actual
- ✅ Evitar over-engineering
- ✅ Agregar complejidad solo cuando sea necesario

### EVITAR
- ❌ Interfaces innecesarias
- ❌ Patrones complejos sin justificación
- ❌ Abstracciones prematuras
- ❌ Código especulativo

---

## 🏗️ Estructura del Proyecto

```
com.example.ticketero/
├── controller/     # REST Controllers
├── service/        # Lógica de negocio
├── repository/     # Acceso a datos
├── model/
│   ├── entity/     # JPA Entities
│   └── dto/        # DTOs (Request/Response)
├── config/         # Configuraciones
├── scheduler/      # Tareas programadas
└── exception/      # Excepciones custom
```

**Responsabilidades:**
- **Controller:** Recibe HTTP, valida (`@Valid`), delega, retorna HTTP
- **Service:** Lógica de negocio, transacciones, orquestación
- **Repository:** Solo acceso a datos (queries)

---

## ☕ Convenciones Java 21

### Nombres
```java
// Clases: PascalCase
public class TicketService { }

// Métodos y variables: camelCase
public void createTicket() { }
private String ticketNumber;

// Constantes: UPPER_SNAKE_CASE
public static final String DEFAULT_QUEUE = "GENERAL";
```

### Features Java 21
```java
// Records para DTOs inmutables
public record TicketResponse(UUID id, String numero) { }

// Text Blocks para SQL/mensajes largos
String query = """
    SELECT t FROM Ticket t
    WHERE t.status = :status
    """;

// Pattern Matching
if (obj instanceof TicketResponse response) {
    return response.numero();
}
```

---

## 🌱 Spring Boot

### Inyección de Dependencias
```java
// ✅ CORRECTO: Constructor injection
@Service
@RequiredArgsConstructor // Lombok
public class TicketService {
    private final TicketRepository repository;
}

// ❌ EVITAR: Field injection
@Autowired
private TicketRepository repository;
```

### Controllers
```java
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class TicketController {
    private final TicketService service;
    
    @PostMapping("/ticket")
    public ResponseEntity<TicketResponse> create(
        @Valid @RequestBody TicketRequest request
    ) {
        return ResponseEntity.ok(service.createTicket(request));
    }
}
```

### Services
```java
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true) // Por defecto
public class TicketService {
    
    @Transactional // Solo escritura
    public TicketResponse createTicket(TicketRequest request) {
        // 1. Validar
        // 2. Lógica
        // 3. Persistir
        // 4. Retornar
    }
}
```

### Repositories
```java
@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    // Query derivada
    Optional<Ticket> findByCodigoReferencia(UUID codigo);
    
    // Query compleja
    @Query("SELECT t FROM Ticket t WHERE t.status = :status")
    List<Ticket> findByStatus(@Param("status") String status);
}
```

---

## 🗄️ JPA y Base de Datos

### Entidades
```java
@Entity
@Table(name = "ticket")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "codigo_referencia", unique = true, nullable = false)
    private UUID codigoReferencia;
    
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
    
    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL)
    @ToString.Exclude // Evitar lazy loading issues
    private List<Mensaje> mensajes;
}
```

### Migraciones Flyway
```sql
-- V1__create_ticket_table.sql
CREATE TABLE ticket (
    id BIGSERIAL PRIMARY KEY,
    codigo_referencia UUID UNIQUE NOT NULL,
    national_id VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ticket_national_id ON ticket(national_id);
```

---

## 📦 DTOs y Validación

### DTOs con Records
```java
public record TicketRequest(
    @NotBlank(message = "ID nacional obligatorio")
    @Pattern(regexp = "^[0-9]{8,12}$")
    String nationalId,
    
    @Pattern(regexp = "^[0-9]{9,15}$")
    String telefono,
    
    @NotBlank
    String branchOffice,
    
    @NotBlank
    String queue
) { }
```

### Exception Handling
```java
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
        MethodArgumentNotValidException ex
    ) {
        String message = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
            .body(new ErrorResponse(message, 400, LocalDateTime.now()));
    }
}
```

---

## 🔒 Seguridad

### Validación
```java
// ✅ Validar en controller
@PostMapping("/ticket")
public ResponseEntity<TicketResponse> create(
    @Valid @RequestBody TicketRequest request // @Valid crítico
) { }
```

### Logs Seguros
```java
// ✅ NO loggear info sensible
log.info("Creating ticket for user: {}", maskId(request.nationalId()));

// ❌ NUNCA
log.info("Token: {}", telegramBotToken); // NO!
```

### Variables de Entorno
```java
// ✅ SIEMPRE
@Value("${telegram.bot-token}")
private String botToken;
```

---

## 🧪 Testing

### Unit Tests
```java
@ExtendWith(MockitoExtension.class)
class TicketServiceTest {
    @Mock
    private TicketRepository repository;
    
    @InjectMocks
    private TicketService service;
    
    @Test
    void createTicket_withValidData_shouldReturnResponse() {
        // Given
        TicketRequest request = buildRequest();
        when(repository.save(any())).thenReturn(buildTicket());
        
        // When
        TicketResponse response = service.createTicket(request);
        
        // Then
        assertNotNull(response);
        verify(repository, times(1)).save(any());
    }
}
```

### Nomenclatura
```
Patrón: methodName_condition_expectedBehavior

Ejemplos:
- createTicket_withValidData_shouldReturnResponse()
- createTicket_withNullId_shouldThrowException()
```

---

## 📝 Logging

```java
@Slf4j
public class TicketService {
    
    public void method() {
        log.info("Operación importante");        // INFO
        log.debug("Detalle técnico");            // DEBUG
        log.warn("Situación anómala manejable"); // WARN
        log.error("Error que requiere atención", e); // ERROR
    }
}
```

---

## 🚀 Performance

### Evitar N+1
```java
// ❌ N+1
List<Ticket> tickets = repository.findAll();
tickets.forEach(t -> t.getMensajes().size());

// ✅ JOIN FETCH
@Query("SELECT t FROM Ticket t LEFT JOIN FETCH t.mensajes")
List<Ticket> findAllWithMensajes();
```

### Paginación
```java
@GetMapping("/tickets")
public ResponseEntity<Page<TicketResponse>> list(
    @PageableDefault(size = 20) Pageable pageable
) {
    return ResponseEntity.ok(service.findAll(pageable));
}
```

---

## 🚫 QUÉ NO HACER

### NO Interfaces Innecesarias
```java
// ❌ PROHIBIDO
public interface TicketService { }
public class TicketServiceImpl implements TicketService { }

// ✅ CORRECTO
@Service
public class TicketService { }
```

### NO Patrones Complejos
```java
// ❌ PROHIBIDO (sin necesidad real)
public interface NotificationStrategy { }
public class NotificationFactory { }

// ✅ CORRECTO
@Service
public class TelegramService { }
```

### NO Mappers Automáticos
```java
// ❌ PROHIBIDO: MapStruct, ModelMapper
public class TicketMapper { }

// ✅ CORRECTO
private TicketResponse toResponse(Ticket ticket) {
    return new TicketResponse(ticket.getId(), ticket.getNumero());
}
```

### NO DTOs Excesivos
```java
// ❌ PROHIBIDO
TicketCreateRequestDTO, TicketUpdateRequestDTO, 
TicketDetailResponseDTO, TicketSummaryResponseDTO

// ✅ CORRECTO: Solo lo necesario
TicketRequest, TicketResponse, ErrorResponse
```

---

## ✅ Checklist Pre-Commit

- [ ] Nombres descriptivos
- [ ] Métodos < 50 líneas
- [ ] Sin código comentado
- [ ] Sin imports no usados
- [ ] Tests escritos
- [ ] Sin info sensible
- [ ] DTOs en API (no entities)
- [ ] Transacciones apropiadas

---

## 🎯 Principios (En Orden)

1. **YAGNI** - No lo agregues hasta que lo necesites
2. **KISS** - Mantenlo simple
3. **Clean Code** - Nombres claros
4. **DRY** - Solo después de 3 repeticiones
5. **SOLID** - Con moderación

---

## 📚 Referencias

- [Spring Boot Docs](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Effective Java - Joshua Bloch](https://www.oreilly.com/library/view/effective-java/9780134686097/)
- [Clean Code - Robert Martin](https://www.oreilly.com/library/view/clean-code-a/9780136083238/)

---

**Versión:** 2.0 (Simplificada)  
**Mantenido por:** Equipo de Desarrollo
