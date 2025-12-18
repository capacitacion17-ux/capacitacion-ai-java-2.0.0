# Framework de Pruebas No Funcionales - Sistema Ticketero

## 🎯 Objetivo

Framework completo de pruebas no funcionales para validar **performance**, **concurrencia**, **resiliencia** y **consistencia** del sistema Ticketero bajo condiciones críticas de producción.

## 📋 Requisitos No Funcionales (SLA)

| ID | Requisito | Métrica | Umbral | Estado |
|---|---|---|---|---|
| RNF-01 | Throughput | Tickets/minuto | ≥ 50 | 🔍 |
| RNF-02 | Latencia | p95 response time | < 2s | 🔍 |
| RNF-03 | Concurrencia | Race conditions | 0 | 🔍 |
| RNF-04 | Consistencia | Tickets inconsistentes | 0 | 🔍 |
| RNF-05 | Recovery | Detección worker muerto | < 90s | 🔍 |
| RNF-06 | Disponibilidad | Uptime bajo carga | 99.9% | 🔍 |
| RNF-07 | Recursos | Memory leak | 0 (30 min) | 🔍 |

## 🏗️ Estructura del Framework

```
ticketero/
├── scripts/
│   ├── performance/           # Tests de rendimiento
│   │   ├── load-test.sh      # Carga sostenida (50+ tickets/min)
│   │   ├── spike-test.sh     # Picos de carga (50 simultáneos)
│   │   └── soak-test.sh      # Prueba prolongada (30 min)
│   ├── concurrency/          # Tests de concurrencia
│   │   ├── race-condition-test.sh    # SELECT FOR UPDATE
│   │   └── idempotency-test.sh       # Operaciones duplicadas
│   ├── resilience/           # Tests de resiliencia
│   │   ├── worker-crash-test.sh      # Auto-recovery workers
│   │   └── rabbitmq-failure-test.sh  # Fallas de mensajería
│   ├── consistency/          # Tests de consistencia
│   │   └── outbox-atomicity-test.sh  # Atomicidad transaccional
│   ├── utils/                # Utilidades
│   │   ├── metrics-collector.sh      # Recolector de métricas
│   │   └── validate-consistency.sh   # Validador de consistencia
│   ├── run-all-nfr-tests.sh  # Suite completa
│   ├── generate-nfr-report.sh # Generador de reportes
│   └── make-executable.bat    # Configurar permisos (Windows)
├── k6/
│   └── load-test.js          # Script K6 para load testing
└── results/                  # Resultados y reportes
    ├── metrics-*.csv         # Métricas del sistema
    ├── load-test-*.json      # Resultados K6
    └── nfr-report-*.md       # Reportes finales
```

## 🚀 Inicio Rápido

### 1. Preparación del Entorno

```bash
# Iniciar servicios Ticketero
cd ticketero
docker compose up -d

# Configurar permisos (Windows)
scripts\make-executable.bat

# O manualmente (Linux/Mac)
chmod +x scripts/**/*.sh
```

### 2. Ejecutar Suite Completa

```bash
# Ejecutar todos los tests NFR (8 escenarios)
bash scripts/run-all-nfr-tests.sh
```

### 3. Ejecutar Tests Individuales

```bash
# Performance Tests
bash scripts/performance/load-test.sh      # ~3 minutos
bash scripts/performance/spike-test.sh     # ~2 minutos  
bash scripts/performance/soak-test.sh      # ~30 minutos

# Concurrency Tests
bash scripts/concurrency/race-condition-test.sh  # ~1 minuto
bash scripts/concurrency/idempotency-test.sh     # ~1 minuto

# Resilience Tests
bash scripts/resilience/worker-crash-test.sh     # ~3 minutos
bash scripts/resilience/rabbitmq-failure-test.sh # ~2 minutos

# Consistency Tests
bash scripts/consistency/outbox-atomicity-test.sh # ~1 minuto
```

## 📊 Interpretación de Resultados

### ✅ Test PASSED
- Todos los umbrales SLA cumplidos
- Sistema consistente
- Listo para producción

### ❌ Test FAILED
- Uno o más umbrales SLA no cumplidos
- Posibles inconsistencias detectadas
- Requiere investigación antes de producción

### 📈 Métricas Clave

**Performance:**
- Throughput: tickets procesados por minuto
- Latencia p95: tiempo de respuesta percentil 95
- Memory usage: consumo de memoria durante pruebas

**Concurrencia:**
- Race conditions: asignaciones dobles de tickets
- Deadlocks: bloqueos en base de datos
- Idempotency: operaciones duplicadas

**Resiliencia:**
- Recovery time: tiempo de detección de workers muertos
- Message processing: procesamiento durante fallas
- System availability: disponibilidad bajo carga

**Consistencia:**
- Ticket states: estados inconsistentes
- Outbox atomicity: atomicidad transaccional
- Data integrity: integridad de datos

## 🔧 Configuración Avanzada

### Variables de Entorno

```bash
# K6 Load Test
export BASE_URL="http://localhost:8080"
export VUS=10                    # Virtual users
export DURATION="2m"             # Test duration

# Métricas
export METRICS_INTERVAL=5        # Segundos entre muestras
export METRICS_DURATION=60       # Duración recolección
```

### Personalización de Umbrales

Editar scripts individuales para ajustar umbrales SLA:

```bash
# load-test.sh
THROUGHPUT_THRESHOLD=50          # tickets/min
CONSISTENCY_REQUIRED=true

# race-condition-test.sh  
MAX_DOUBLE_ASSIGNMENTS=0
MAX_DEADLOCKS=0

# worker-crash-test.sh
MAX_RECOVERY_TIME=90             # segundos
```

## 📋 Checklist Pre-Producción

### Performance ✅
- [ ] Throughput ≥ 50 tickets/min
- [ ] Latencia p95 < 2000ms
- [ ] Memory leak < 100MB en 30min
- [ ] CPU usage < 80% bajo carga

### Concurrencia ✅
- [ ] 0 race conditions detectadas
- [ ] 0 deadlocks en PostgreSQL
- [ ] Idempotency garantizada
- [ ] SELECT FOR UPDATE funcionando

### Resiliencia ✅
- [ ] Auto-recovery < 90s
- [ ] Mensajería resiliente a fallas
- [ ] Workers auto-recuperables
- [ ] Sistema disponible 99.9%

### Consistencia ✅
- [ ] 0 tickets inconsistentes
- [ ] Atomicidad Ticket + Outbox
- [ ] Integridad referencial
- [ ] Estados válidos siempre

## 🐛 Troubleshooting

### Error: "Docker no está corriendo"
```bash
# Windows
docker-desktop start

# Linux
sudo systemctl start docker
```

### Error: "Servicios no disponibles"
```bash
# Reiniciar servicios
docker compose down
docker compose up -d

# Verificar estado
docker compose ps
```

### Error: "K6 no encontrado"
```bash
# Los scripts tienen fallback a curl
# Para instalar K6:
# Windows: choco install k6
# Linux: sudo apt install k6
# Mac: brew install k6
```

### Error: "Permisos de ejecución"
```bash
# Windows
scripts\make-executable.bat

# Linux/Mac
find scripts -name "*.sh" -exec chmod +x {} \;
```

## 📈 Monitoreo Continuo

### Integración CI/CD

```yaml
# .github/workflows/nfr-tests.yml
name: NFR Tests
on: [push, pull_request]
jobs:
  nfr-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Run NFR Tests
        run: |
          cd ticketero
          docker compose up -d
          bash scripts/run-all-nfr-tests.sh
```

### Alertas de Performance

```bash
# Configurar alertas basadas en métricas
# Throughput < 50 tickets/min → Alert
# Latencia p95 > 2000ms → Alert  
# Memory leak > 100MB → Alert
```

## 🎯 Próximos Pasos

1. **Ejecutar suite completa** y validar resultados
2. **Ajustar umbrales** según capacidad del entorno
3. **Integrar en CI/CD** para validación continua
4. **Configurar monitoreo** en producción
5. **Documentar hallazgos** y optimizaciones

---

**Tiempo estimado total:** 45-60 minutos
**Cobertura NFR:** 100% (8/8 escenarios críticos)
**Automatización:** Completa con reportes integrados