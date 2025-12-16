# **PROMPT 6C: PRUEBAS NO FUNCIONALES \- Performance, Concurrencia y Resiliencia**

## **Contexto**

Eres un Performance Engineer Senior experto en testing no funcional. Tu tarea es diseñar e implementar **pruebas de performance, concurrencia y resiliencia** para el sistema Ticketero, validando que cumple con los requisitos no funcionales críticos.

**Características del proyecto:**

* API REST con Spring Boot 3.2, Java 21  
* PostgreSQL 16 \+ RabbitMQ 3.13 \+ Telegram Bot API  
* Patrón Outbox para mensajería confiable  
* 3 workers concurrentes por cola (12 total)  
* SELECT FOR UPDATE para evitar race conditions  
* Auto-recovery de workers muertos (heartbeat 60s)

**IMPORTANTE:** Después de completar CADA paso, debes DETENERTE y solicitar una **revisión exhaustiva** antes de continuar.

---

## **Requisitos No Funcionales a Validar**

| ID | Requisito | Métrica | Umbral |
| ----- | ----- | ----- | ----- |
| RNF-01 | Throughput | Tickets procesados/minuto | ≥ 50 |
| RNF-02 | Latencia API | p95 response time | \< 2 segundos |
| RNF-03 | Concurrencia | Race conditions | 0 detectadas |
| RNF-04 | Consistencia | Tickets inconsistentes | 0 |
| RNF-05 | Recovery Time | Detección worker muerto | \< 90 segundos |
| RNF-06 | Disponibilidad | Uptime durante carga | 99.9% |
| RNF-07 | Recursos | Memory leak | 0 (estable 30 min) |

---

## **Documentos de Entrada**

**Lee estos archivos del proyecto:**

1. `src/main/java/com/example/ticketero/consumer/TicketWorker.java` \- Workers RabbitMQ  
2. `src/main/java/com/example/ticketero/service/RecoveryService.java` \- Auto-recovery  
3. `src/main/java/com/example/ticketero/config/GracefulShutdownConfig.java` \- Shutdown  
4. `src/main/resources/application.yml` \- Configuración de concurrencia  
5. `docker-compose.yml` \- Infraestructura

---

## **Metodología de Trabajo**

### **Principio:**

**"Diseñar → Implementar → Ejecutar → Analizar → Confirmar → Continuar"**

Después de CADA paso:

1. ✅ Diseña los escenarios de prueba  
2. ✅ Implementa scripts/tests  
3. ✅ Ejecuta y captura métricas  
4. ✅ Analiza resultados vs umbrales  
5. ⏸️ **DETENTE y solicita revisión**  
6. ✅ Espera confirmación antes de continuar

### **Formato de Solicitud de Revisión:**

✅ PASO X COMPLETADO

Escenarios ejecutados:  
\- \[Escenario 1\]: PASS/FAIL  
\- \[Escenario 2\]: PASS/FAIL

Métricas capturadas:  
\- Throughput: X tickets/min (umbral: ≥50)  
\- Latencia p95: Xms (umbral: \<2000ms)  
\- Errores: X% (umbral: \<1%)

🔍 SOLICITO REVISIÓN:

1\. ¿Los resultados son aceptables?  
2\. ¿Hay ajustes necesarios?  
3\. ¿Puedo continuar con el siguiente paso?

⏸️ ESPERANDO CONFIRMACIÓN...

---

## **Tu Tarea: 8 Pasos**

**PASO 1:** Setup de Herramientas \+ Scripts Base  
**PASO 2:** Performance \- Load Test Sostenido (3 escenarios)  
**PASO 3:** Concurrencia \- Race Conditions (3 escenarios)  
**PASO 4:** Resiliencia \- Auto-Recovery (3 escenarios)  
**PASO 5:** Consistencia \- Outbox Pattern (2 escenarios)  
**PASO 6:** Graceful Shutdown (2 escenarios)  
**PASO 7:** Escalabilidad (2 escenarios)  
**PASO 8:** Reporte Final y Dashboard

**Total:** \~15 escenarios | Cobertura NFR: 100%

---

## **Estructura de Archivos a Crear**

ticketero/  
├── scripts/  
│   ├── performance/  
│   │   ├── load-test.sh  
│   │   ├── spike-test.sh  
│   │   └── soak-test.sh  
│   ├── concurrency/  
│   │   ├── race-condition-test.sh  
│   │   └── idempotency-test.sh  
│   ├── resilience/  
│   │   ├── worker-crash-test.sh  
│   │   ├── rabbitmq-failure-test.sh  
│   │   └── recovery-test.sh  
│   ├── chaos/  
│   │   ├── kill-worker.sh  
│   │   └── network-delay.sh  
│   └── utils/  
│       ├── metrics-collector.sh  
│       └── validate-consistency.sh  
├── k6/  
│   ├── load-test.js  
│   ├── spike-test.js  
│   └── stress-test.js  
└── docs/  
    └── NFR-TEST-RESULTS.md

---

## **PASO 1: Setup de Herramientas \+ Scripts Base**

**Objetivo:** Configurar herramientas de testing y scripts utilitarios.

### **1.1 metrics-collector.sh**

\#\!/bin/bash  
\# \=============================================================================  
\# TICKETERO \- Metrics Collector  
\# \=============================================================================  
\# Recolecta métricas del sistema durante pruebas de performance  
\# Usage: ./scripts/utils/metrics-collector.sh \[duration\_seconds\] \[output\_file\]  
\# \=============================================================================

DURATION=${1:-60}  
OUTPUT\_FILE=${2:-"metrics-$(date \+%Y%m%d-%H%M%S).csv"}

echo "timestamp,cpu\_app,mem\_app\_mb,cpu\_postgres,mem\_postgres\_mb,cpu\_rabbitmq,mem\_rabbitmq\_mb,db\_connections,rabbitmq\_messages,tickets\_waiting,tickets\_completed,outbox\_pending,outbox\_failed" \> "$OUTPUT\_FILE"

echo "📊 Collecting metrics for ${DURATION} seconds..."  
echo "📁 Output: ${OUTPUT\_FILE}"

START\_TIME=$(date \+%s)  
END\_TIME=$((START\_TIME \+ DURATION))

while \[ $(date \+%s) \-lt $END\_TIME \]; do  
    TIMESTAMP=$(date \+%Y-%m-%d\\ %H:%M:%S)  
      
    \# Container stats  
    APP\_STATS=$(docker stats ticketero-app \--no-stream \--format "{{.CPUPerc}},{{.MemUsage}}" 2\>/dev/null | head \-1)  
    APP\_CPU=$(echo "$APP\_STATS" | cut \-d',' \-f1 | tr \-d '%')  
    APP\_MEM=$(echo "$APP\_STATS" | cut \-d',' \-f2 | cut \-d'/' \-f1 | tr \-d 'MiB ')  
      
    PG\_STATS=$(docker stats ticketero-postgres \--no-stream \--format "{{.CPUPerc}},{{.MemUsage}}" 2\>/dev/null | head \-1)  
    PG\_CPU=$(echo "$PG\_STATS" | cut \-d',' \-f1 | tr \-d '%')  
    PG\_MEM=$(echo "$PG\_STATS" | cut \-d',' \-f2 | cut \-d'/' \-f1 | tr \-d 'MiB ')  
      
    MQ\_STATS=$(docker stats ticketero-rabbitmq \--no-stream \--format "{{.CPUPerc}},{{.MemUsage}}" 2\>/dev/null | head \-1)  
    MQ\_CPU=$(echo "$MQ\_STATS" | cut \-d',' \-f1 | tr \-d '%')  
    MQ\_MEM=$(echo "$MQ\_STATS" | cut \-d',' \-f2 | cut \-d'/' \-f1 | tr \-d 'MiB ')  
      
    \# Database metrics  
    DB\_CONNECTIONS=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
        "SELECT count(\*) FROM pg\_stat\_activity WHERE datname='ticketero';" 2\>/dev/null | xargs)  
      
    \# RabbitMQ messages  
    MQ\_MESSAGES=$(docker exec ticketero-rabbitmq rabbitmqctl list\_queues messages 2\>/dev/null | \\  
        grep \-v "Listing\\|Timeout" | awk '{sum+=$2} END {print sum}')  
      
    \# Ticket stats  
    TICKETS\_WAITING=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
        "SELECT COUNT(\*) FROM ticket WHERE status='WAITING';" 2\>/dev/null | xargs)  
    TICKETS\_COMPLETED=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
        "SELECT COUNT(\*) FROM ticket WHERE status='COMPLETED';" 2\>/dev/null | xargs)  
      
    \# Outbox stats  
    OUTBOX\_PENDING=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
        "SELECT COUNT(\*) FROM outbox\_message WHERE status='PENDING';" 2\>/dev/null | xargs)  
    OUTBOX\_FAILED=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
        "SELECT COUNT(\*) FROM outbox\_message WHERE status='FAILED';" 2\>/dev/null | xargs)  
      
    \# Write to CSV  
    echo "${TIMESTAMP},${APP\_CPU:-0},${APP\_MEM:-0},${PG\_CPU:-0},${PG\_MEM:-0},${MQ\_CPU:-0},${MQ\_MEM:-0},${DB\_CONNECTIONS:-0},${MQ\_MESSAGES:-0},${TICKETS\_WAITING:-0},${TICKETS\_COMPLETED:-0},${OUTBOX\_PENDING:-0},${OUTBOX\_FAILED:-0}" \>\> "$OUTPUT\_FILE"  
      
    sleep 5  
done

echo "✅ Metrics collection complete: ${OUTPUT\_FILE}"

### **1.2 validate-consistency.sh**

\#\!/bin/bash  
\# \=============================================================================  
\# TICKETERO \- Consistency Validator  
\# \=============================================================================  
\# Valida consistencia del sistema después de pruebas de carga  
\# Usage: ./scripts/utils/validate-consistency.sh  
\# \=============================================================================

RED='\\033\[0;31m'  
GREEN='\\033\[0;32m'  
YELLOW='\\033\[1;33m'  
NC='\\033\[0m'

echo "═══════════════════════════════════════════════════════════════"  
echo "  TICKETERO \- VALIDACIÓN DE CONSISTENCIA"  
echo "═══════════════════════════════════════════════════════════════"  
echo ""

ERRORS=0

\# 1\. Tickets en estado inconsistente  
echo \-n "1. Tickets en estado inconsistente... "  
INCONSISTENT=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c "  
    SELECT COUNT(\*) FROM ticket t  
    WHERE (t.status \= 'IN\_PROGRESS' AND t.started\_at IS NULL)  
       OR (t.status \= 'COMPLETED' AND t.completed\_at IS NULL)  
       OR (t.status \= 'CALLED' AND t.assigned\_advisor\_id IS NULL);  
" | xargs)

if \[ "$INCONSISTENT" \-eq 0 \]; then  
    echo \-e "${GREEN}PASS${NC} (0 encontrados)"  
else  
    echo \-e "${RED}FAIL${NC} ($INCONSISTENT encontrados)"  
    ERRORS=$((ERRORS \+ 1))  
fi

\# 2\. Asesores en estado inconsistente  
echo \-n "2. Asesores BUSY sin ticket activo... "  
BUSY\_NO\_TICKET=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c "  
    SELECT COUNT(\*) FROM advisor a  
    WHERE a.status \= 'BUSY'  
    AND NOT EXISTS (  
        SELECT 1 FROM ticket t   
        WHERE t.assigned\_advisor\_id \= a.id   
        AND t.status IN ('CALLED', 'IN\_PROGRESS')  
    );  
" | xargs)

if \[ "$BUSY\_NO\_TICKET" \-eq 0 \]; then  
    echo \-e "${GREEN}PASS${NC} (0 encontrados)"  
else  
    echo \-e "${YELLOW}WARN${NC} ($BUSY\_NO\_TICKET encontrados \- recovery pendiente)"  
fi

\# 3\. Mensajes Outbox fallidos  
echo \-n "3. Mensajes Outbox FAILED... "  
OUTBOX\_FAILED=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT COUNT(\*) FROM outbox\_message WHERE status='FAILED';" | xargs)

if \[ "$OUTBOX\_FAILED" \-eq 0 \]; then  
    echo \-e "${GREEN}PASS${NC} (0 fallidos)"  
else  
    echo \-e "${RED}FAIL${NC} ($OUTBOX\_FAILED mensajes fallidos)"  
    ERRORS=$((ERRORS \+ 1))  
fi

\# 4\. Tickets duplicados (mismo nationalId \+ cola en estado activo)  
echo \-n "4. Tickets potencialmente duplicados... "  
DUPLICATES=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c "  
    SELECT COUNT(\*) FROM (  
        SELECT national\_id, queue\_type, COUNT(\*) as cnt  
        FROM ticket  
        WHERE status IN ('WAITING', 'CALLED', 'IN\_PROGRESS')  
        GROUP BY national\_id, queue\_type  
        HAVING COUNT(\*) \> 1  
    ) dups;  
" | xargs)

if \[ "$DUPLICATES" \-eq 0 \]; then  
    echo \-e "${GREEN}PASS${NC} (0 duplicados)"  
else  
    echo \-e "${YELLOW}WARN${NC} ($DUPLICATES posibles duplicados)"  
fi

\# 5\. Recovery events recientes  
echo \-n "5. Recovery events (últimas 24h)... "  
RECOVERIES=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c "  
    SELECT COUNT(\*) FROM recovery\_event   
    WHERE detected\_at \> NOW() \- INTERVAL '24 hours';  
" | xargs)

if \[ "$RECOVERIES" \-eq 0 \]; then  
    echo \-e "${GREEN}OK${NC} (0 recuperaciones)"  
else  
    echo \-e "${YELLOW}INFO${NC} ($RECOVERIES recuperaciones automáticas)"  
fi

\# 6\. Conexiones DB abiertas  
echo \-n "6. Conexiones PostgreSQL... "  
DB\_CONN=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT count(\*) FROM pg\_stat\_activity WHERE datname='ticketero';" | xargs)

if \[ "$DB\_CONN" \-lt 20 \]; then  
    echo \-e "${GREEN}OK${NC} ($DB\_CONN conexiones)"  
else  
    echo \-e "${YELLOW}WARN${NC} ($DB\_CONN conexiones \- revisar pool)"  
fi

\# 7\. Mensajes en colas RabbitMQ  
echo \-n "7. Mensajes pendientes en RabbitMQ... "  
MQ\_PENDING=$(docker exec ticketero-rabbitmq rabbitmqctl list\_queues messages 2\>/dev/null | \\  
    grep \-v "Listing\\|Timeout" | awk '{sum+=$2} END {print sum}')

if \[ "${MQ\_PENDING:-0}" \-lt 10 \]; then  
    echo \-e "${GREEN}OK${NC} (${MQ\_PENDING:-0} mensajes)"  
else  
    echo \-e "${YELLOW}WARN${NC} (${MQ\_PENDING:-0} mensajes acumulados)"  
fi

echo ""  
echo "═══════════════════════════════════════════════════════════════"  
if \[ $ERRORS \-eq 0 \]; then  
    echo \-e "  RESULTADO: ${GREEN}SISTEMA CONSISTENTE${NC}"  
else  
    echo \-e "  RESULTADO: ${RED}$ERRORS ERRORES DE CONSISTENCIA${NC}"  
fi  
echo "═══════════════════════════════════════════════════════════════"

exit $ERRORS

### **1.3 k6/load-test.js (K6 Script Base)**

// \=============================================================================  
// TICKETERO \- K6 Load Test Base  
// \=============================================================================  
// Usage: k6 run \--vus 10 \--duration 2m k6/load-test.js  
// \=============================================================================

import http from 'k6/http';  
import { check, sleep } from 'k6';  
import { Counter, Rate, Trend } from 'k6/metrics';

// Custom metrics  
const ticketsCreated \= new Counter('tickets\_created');  
const ticketErrors \= new Rate('ticket\_errors');  
const createLatency \= new Trend('create\_latency', true);

// Configuration  
const BASE\_URL \= \_\_ENV.BASE\_URL || 'http://localhost:8080';  
const QUEUES \= \['CAJA', 'PERSONAL', 'EMPRESAS', 'GERENCIA'\];

// Test options (can be overridden via CLI)  
export const options \= {  
    vus: 10,  
    duration: '2m',  
    thresholds: {  
        http\_req\_duration: \['p(95)\<2000'\],  // p95 \< 2s  
        ticket\_errors: \['rate\<0.01'\],        // \< 1% errors  
        tickets\_created: \['count\>50'\],       // \> 50 tickets  
    },  
};

// Unique ID generator  
function generateNationalId() {  
    return Math.floor(10000000 \+ Math.random() \* 90000000).toString();  
}

function generatePhone() {  
    return '+569' \+ Math.floor(10000000 \+ Math.random() \* 90000000);  
}

// Main test function  
export default function () {  
    const queue \= QUEUES\[Math.floor(Math.random() \* QUEUES.length)\];  
      
    const payload \= JSON.stringify({  
        nationalId: generateNationalId(),  
        telefono: generatePhone(),  
        branchOffice: 'Sucursal Centro',  
        queueType: queue,  
    });

    const params \= {  
        headers: {  
            'Content-Type': 'application/json',  
        },  
        tags: { name: 'CreateTicket' },  
    };

    const startTime \= Date.now();  
    const response \= http.post(\`${BASE\_URL}/api/tickets\`, payload, params);  
    const duration \= Date.now() \- startTime;

    // Record metrics  
    createLatency.add(duration);

    const success \= check(response, {  
        'status is 201': (r) \=\> r.status \=== 201,  
        'has ticket number': (r) \=\> r.json('numero') \!== undefined,  
        'has position': (r) \=\> r.json('positionInQueue') \> 0,  
    });

    if (success) {  
        ticketsCreated.add(1);  
    } else {  
        ticketErrors.add(1);  
        console.log(\`Error: ${response.status} \- ${response.body}\`);  
    }

    // Think time between requests  
    sleep(Math.random() \* 2 \+ 1); // 1-3 seconds  
}

// Summary handler  
export function handleSummary(data) {  
    return {  
        'stdout': textSummary(data, { indent: ' ', enableColors: true }),  
        'results/load-test-summary.json': JSON.stringify(data, null, 2),  
    };  
}

function textSummary(data, options) {  
    const checks \= data.metrics.checks;  
    const duration \= data.metrics.http\_req\_duration;  
      
    return \`  
═══════════════════════════════════════════════════════════════  
  TICKETERO \- LOAD TEST RESULTS  
═══════════════════════════════════════════════════════════════

  Total Requests:    ${data.metrics.http\_reqs.values.count}  
  Tickets Created:   ${data.metrics.tickets\_created?.values.count || 0}  
  Error Rate:        ${(data.metrics.ticket\_errors?.values.rate \* 100 || 0).toFixed(2)}%

  Latency:  
    p50:  ${duration.values\['p(50)'\].toFixed(0)}ms  
    p95:  ${duration.values\['p(95)'\].toFixed(0)}ms  
    p99:  ${duration.values\['p(99)'\].toFixed(0)}ms  
    max:  ${duration.values.max.toFixed(0)}ms

  Throughput:        ${(data.metrics.http\_reqs.values.count / (data.state.testRunDurationMs / 1000 / 60)).toFixed(1)} req/min

═══════════════════════════════════════════════════════════════  
\`;  
}

**Validaciones:**

chmod \+x scripts/utils/\*.sh  
./scripts/utils/validate-consistency.sh  
\# All checks should pass

**🔍 PUNTO DE REVISIÓN 1:**

✅ PASO 1 COMPLETADO

Scripts creados:  
\- metrics-collector.sh: Recolecta métricas cada 5s  
\- validate-consistency.sh: 7 validaciones de consistencia  
\- k6/load-test.js: Script base K6 con métricas custom

Herramientas configuradas:  
\- K6 para load testing  
\- Bash scripts para chaos testing  
\- CSV output para análisis

🔍 SOLICITO REVISIÓN:  
1\. ¿Los scripts cubren las métricas necesarias?  
2\. ¿Puedo continuar con PASO 2?

⏸️ ESPERANDO CONFIRMACIÓN...

---

## **PASO 2: Performance \- Load Test Sostenido**

**Objetivo:** Validar throughput ≥50 tickets/min y latencia p95 \<2s.

### **Escenarios**

Test: PERF-01 Load Test Sostenido  
Category: Performance  
Priority: P1

Objetivo: Validar throughput sostenido de 50+ tickets/minuto

Setup:  
  \- Sistema limpio (DB sin tickets previos)  
  \- 5 asesores AVAILABLE  
  \- Telegram mock activo

Execution:  
  \- 100 tickets en 2 minutos (distribución uniforme)  
  \- 10 VUs concurrentes  
  \- Think time: 1-3 segundos

Success Criteria:  
  \- Throughput: ≥ 50 tickets/minuto  
  \- Latencia p95: \< 2000ms  
  \- Error rate: \< 1%  
  \- Sin deadlocks en BD  
  \- Sin mensajes perdidos en RabbitMQ

### **2.1 load-test.sh**

\#\!/bin/bash  
\# \=============================================================================  
\# TICKETERO \- Load Test Sostenido  
\# \=============================================================================  
\# Ejecuta test de carga sostenida: 100 tickets en 2 minutos  
\# Usage: ./scripts/performance/load-test.sh  
\# \=============================================================================

set \-e

SCRIPT\_DIR="$(cd "$(dirname "${BASH\_SOURCE\[0\]}")" && pwd)"  
PROJECT\_ROOT="$(cd "$SCRIPT\_DIR/../.." && pwd)"

RED='\\033\[0;31m'  
GREEN='\\033\[0;32m'  
YELLOW='\\033\[1;33m'  
CYAN='\\033\[0;36m'  
NC='\\033\[0m'

echo \-e "${CYAN}╔══════════════════════════════════════════════════════════════╗${NC}"  
echo \-e "${CYAN}║        TICKETERO \- LOAD TEST SOSTENIDO (PERF-01)             ║${NC}"  
echo \-e "${CYAN}╚══════════════════════════════════════════════════════════════╝${NC}"  
echo ""

\# \=============================================================================  
\# 1\. PRE-TEST CLEANUP  
\# \=============================================================================  
echo \-e "${YELLOW}1. Limpiando estado previo...${NC}"

docker exec ticketero-postgres psql \-U dev \-d ticketero \-c "  
    DELETE FROM ticket\_event;  
    DELETE FROM recovery\_event;  
    DELETE FROM outbox\_message;  
    DELETE FROM ticket;  
    UPDATE advisor SET status \= 'AVAILABLE', total\_tickets\_served \= 0;  
" \> /dev/null 2\>&1

echo "   ✓ Base de datos limpia"

\# \=============================================================================  
\# 2\. CAPTURE BASELINE  
\# \=============================================================================  
echo \-e "${YELLOW}2. Capturando baseline...${NC}"

ADVISORS\_AVAILABLE=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT COUNT(\*) FROM advisor WHERE status='AVAILABLE';" | xargs)  
echo "   ✓ Asesores disponibles: $ADVISORS\_AVAILABLE"

\# \=============================================================================  
\# 3\. START METRICS COLLECTION (background)  
\# \=============================================================================  
echo \-e "${YELLOW}3. Iniciando recolección de métricas...${NC}"

METRICS\_FILE="$PROJECT\_ROOT/results/load-test-metrics-$(date \+%Y%m%d-%H%M%S).csv"  
mkdir \-p "$PROJECT\_ROOT/results"

"$SCRIPT\_DIR/../utils/metrics-collector.sh" 150 "$METRICS\_FILE" &  
METRICS\_PID=$\!  
echo "   ✓ Métricas: $METRICS\_FILE (PID: $METRICS\_PID)"

\# \=============================================================================  
\# 4\. EXECUTE LOAD TEST  
\# \=============================================================================  
echo \-e "${YELLOW}4. Ejecutando load test (2 minutos)...${NC}"  
echo ""

START\_TIME=$(date \+%s)

\# Check if K6 is available  
if command \-v k6 &\> /dev/null; then  
    echo "   Usando K6..."  
    k6 run \--vus 10 \--duration 2m "$PROJECT\_ROOT/k6/load-test.js" \\  
        \--out json="$PROJECT\_ROOT/results/load-test-k6.json" 2\>&1 | tee "$PROJECT\_ROOT/results/load-test-output.txt"  
else  
    echo "   K6 no disponible, usando script bash..."  
      
    TICKETS\_TO\_CREATE=100  
    CREATED=0  
    ERRORS=0  
      
    for i in $(seq 1 $TICKETS\_TO\_CREATE); do  
        QUEUE\_INDEX=$((i % 4))  
        QUEUES=("CAJA" "PERSONAL" "EMPRESAS" "GERENCIA")  
        QUEUE=${QUEUES\[$QUEUE\_INDEX\]}  
        NATIONAL\_ID="300000$(printf '%03d' $i)"  
          
        RESPONSE=$(curl \-s \-w "\\n%{http\_code}" \-X POST "http://localhost:8080/api/tickets" \\  
            \-H "Content-Type: application/json" \\  
            \-d "{  
                \\"nationalId\\": \\"${NATIONAL\_ID}\\",  
                \\"telefono\\": \\"+5691234${i}\\",  
                \\"branchOffice\\": \\"Sucursal Test\\",  
                \\"queueType\\": \\"${QUEUE}\\"  
            }")  
          
        HTTP\_CODE=$(echo "$RESPONSE" | tail \-1)  
          
        if \[ "$HTTP\_CODE" \= "201" \]; then  
            CREATED=$((CREATED \+ 1))  
            echo \-ne "\\r   Tickets creados: $CREATED/$TICKETS\_TO\_CREATE"  
        else  
            ERRORS=$((ERRORS \+ 1))  
        fi  
          
        \# Rate limiting: \~50 tickets/min \= 1 ticket/1.2s  
        sleep 1.2  
    done  
      
    echo ""  
    echo "   ✓ Creados: $CREATED, Errores: $ERRORS"  
fi

END\_TIME=$(date \+%s)  
DURATION=$((END\_TIME \- START\_TIME))

\# \=============================================================================  
\# 5\. WAIT FOR PROCESSING  
\# \=============================================================================  
echo \-e "${YELLOW}5. Esperando procesamiento completo...${NC}"

MAX\_WAIT=120  
WAITED=0

while \[ $WAITED \-lt $MAX\_WAIT \]; do  
    WAITING=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
        "SELECT COUNT(\*) FROM ticket WHERE status='WAITING';" | xargs)  
    IN\_PROGRESS=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
        "SELECT COUNT(\*) FROM ticket WHERE status IN ('CALLED', 'IN\_PROGRESS');" | xargs)  
      
    if \[ "$WAITING" \-eq 0 \] && \[ "$IN\_PROGRESS" \-eq 0 \]; then  
        echo "   ✓ Todos los tickets procesados"  
        break  
    fi  
      
    echo \-ne "\\r   Esperando... WAITING: $WAITING, IN\_PROGRESS: $IN\_PROGRESS    "  
    sleep 5  
    WAITED=$((WAITED \+ 5))  
done

echo ""

\# \=============================================================================  
\# 6\. STOP METRICS COLLECTION  
\# \=============================================================================  
kill $METRICS\_PID 2\>/dev/null || true  
echo "   ✓ Recolección de métricas detenida"

\# \=============================================================================  
\# 7\. COLLECT RESULTS  
\# \=============================================================================  
echo \-e "${YELLOW}6. Recolectando resultados...${NC}"

TOTAL\_TICKETS=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT COUNT(\*) FROM ticket;" | xargs)  
COMPLETED\_TICKETS=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT COUNT(\*) FROM ticket WHERE status='COMPLETED';" | xargs)  
FAILED\_OUTBOX=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT COUNT(\*) FROM outbox\_message WHERE status='FAILED';" | xargs)

THROUGHPUT=$(echo "scale=1; $COMPLETED\_TICKETS \* 60 / $DURATION" | bc)

\# \=============================================================================  
\# 8\. VALIDATE CONSISTENCY  
\# \=============================================================================  
echo \-e "${YELLOW}7. Validando consistencia...${NC}"  
"$SCRIPT\_DIR/../utils/validate-consistency.sh"  
CONSISTENCY\_RESULT=$?

\# \=============================================================================  
\# 9\. PRINT RESULTS  
\# \=============================================================================  
echo ""  
echo \-e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"  
echo \-e "${CYAN}  RESULTADOS LOAD TEST SOSTENIDO${NC}"  
echo \-e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"  
echo ""  
echo "  Duración:           ${DURATION} segundos"  
echo "  Tickets creados:    ${TOTAL\_TICKETS}"  
echo "  Tickets completados: ${COMPLETED\_TICKETS}"  
echo "  Outbox fallidos:    ${FAILED\_OUTBOX}"  
echo ""  
echo "  📊 MÉTRICAS:"  
echo "  ─────────────────────────────────────────────"

\# Throughput check  
if (( $(echo "$THROUGHPUT \>= 50" | bc \-l) )); then  
    echo \-e "  Throughput:         ${GREEN}${THROUGHPUT} tickets/min${NC} (≥50 ✓)"  
else  
    echo \-e "  Throughput:         ${RED}${THROUGHPUT} tickets/min${NC} (\<50 ✗)"  
fi

\# Completion check  
COMPLETION\_RATE=$(echo "scale=1; $COMPLETED\_TICKETS \* 100 / $TOTAL\_TICKETS" | bc)  
if (( $(echo "$COMPLETION\_RATE \>= 99" | bc \-l) )); then  
    echo \-e "  Completion rate:    ${GREEN}${COMPLETION\_RATE}%${NC} (≥99% ✓)"  
else  
    echo \-e "  Completion rate:    ${RED}${COMPLETION\_RATE}%${NC} (\<99% ✗)"  
fi

\# Consistency check  
if \[ $CONSISTENCY\_RESULT \-eq 0 \]; then  
    echo \-e "  Consistencia:       ${GREEN}PASS${NC}"  
else  
    echo \-e "  Consistencia:       ${RED}FAIL${NC}"  
fi

echo ""  
echo "  📁 Archivos generados:"  
echo "     \- $METRICS\_FILE"  
echo ""  
echo \-e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"

\# Exit code based on results  
if (( $(echo "$THROUGHPUT \>= 50" | bc \-l) )) && \[ $CONSISTENCY\_RESULT \-eq 0 \]; then  
    echo \-e "${GREEN}✅ LOAD TEST PASSED${NC}"  
    exit 0  
else  
    echo \-e "${RED}❌ LOAD TEST FAILED${NC}"  
    exit 1  
fi

### **2.2 spike-test.sh**

\#\!/bin/bash  
\# \=============================================================================  
\# TICKETERO \- Spike Test  
\# \=============================================================================  
\# Ejecuta test de spike: 50 tickets simultáneos en 10 segundos  
\# Usage: ./scripts/performance/spike-test.sh  
\# \=============================================================================

set \-e

SCRIPT\_DIR="$(cd "$(dirname "${BASH\_SOURCE\[0\]}")" && pwd)"  
PROJECT\_ROOT="$(cd "$SCRIPT\_DIR/../.." && pwd)"

RED='\\033\[0;31m'  
GREEN='\\033\[0;32m'  
YELLOW='\\033\[1;33m'  
CYAN='\\033\[0;36m'  
NC='\\033\[0m'

echo \-e "${CYAN}╔══════════════════════════════════════════════════════════════╗${NC}"  
echo \-e "${CYAN}║        TICKETERO \- SPIKE TEST (PERF-02)                      ║${NC}"  
echo \-e "${CYAN}╚══════════════════════════════════════════════════════════════╝${NC}"  
echo ""

\# Cleanup  
echo \-e "${YELLOW}1. Limpiando estado previo...${NC}"  
docker exec ticketero-postgres psql \-U dev \-d ticketero \-c "  
    DELETE FROM ticket\_event;  
    DELETE FROM recovery\_event;  
    DELETE FROM outbox\_message;  
    DELETE FROM ticket;  
    UPDATE advisor SET status \= 'AVAILABLE', total\_tickets\_served \= 0;  
" \> /dev/null 2\>&1

\# Start metrics  
METRICS\_FILE="$PROJECT\_ROOT/results/spike-test-metrics-$(date \+%Y%m%d-%H%M%S).csv"  
mkdir \-p "$PROJECT\_ROOT/results"  
"$SCRIPT\_DIR/../utils/metrics-collector.sh" 120 "$METRICS\_FILE" &  
METRICS\_PID=$\!

\# Execute spike  
echo \-e "${YELLOW}2. Ejecutando spike (50 tickets en 10 segundos)...${NC}"  
START\_TIME=$(date \+%s)

\# Crear 50 tickets en paralelo  
for i in $(seq 1 50); do  
    (  
        QUEUE\_INDEX=$((i % 4))  
        QUEUES=("CAJA" "PERSONAL" "EMPRESAS" "GERENCIA")  
        QUEUE=${QUEUES\[$QUEUE\_INDEX\]}  
          
        curl \-s \-X POST "http://localhost:8080/api/tickets" \\  
            \-H "Content-Type: application/json" \\  
            \-d "{  
                \\"nationalId\\": \\"400000$(printf '%03d' $i)\\",  
                \\"telefono\\": \\"+5691234${i}\\",  
                \\"branchOffice\\": \\"Sucursal Test\\",  
                \\"queueType\\": \\"${QUEUE}\\"  
            }" \> /dev/null  
    ) &  
done

wait  
SPIKE\_END=$(date \+%s)  
SPIKE\_DURATION=$((SPIKE\_END \- START\_TIME))  
echo "   ✓ Spike completado en ${SPIKE\_DURATION} segundos"

\# Wait for processing  
echo \-e "${YELLOW}3. Esperando procesamiento...${NC}"  
MAX\_WAIT=180  
WAITED=0

while \[ $WAITED \-lt $MAX\_WAIT \]; do  
    COMPLETED=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
        "SELECT COUNT(\*) FROM ticket WHERE status='COMPLETED';" | xargs)  
      
    if \[ "$COMPLETED" \-ge 50 \]; then  
        PROCESS\_END=$(date \+%s)  
        TOTAL\_PROCESS\_TIME=$((PROCESS\_END \- START\_TIME))  
        echo "   ✓ Todos procesados en ${TOTAL\_PROCESS\_TIME} segundos"  
        break  
    fi  
      
    echo \-ne "\\r   Completados: $COMPLETED/50    "  
    sleep 5  
    WAITED=$((WAITED \+ 5))  
done

\# Stop metrics  
kill $METRICS\_PID 2\>/dev/null || true

\# Results  
echo ""  
echo \-e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"  
echo \-e "${CYAN}  RESULTADOS SPIKE TEST${NC}"  
echo \-e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"  
echo ""  
echo "  Tickets creados:     50 en ${SPIKE\_DURATION}s"  
echo "  Tiempo procesamiento: ${TOTAL\_PROCESS\_TIME:-timeout}s"  
echo ""

\# Validate  
"$SCRIPT\_DIR/../utils/validate-consistency.sh"

if \[ "${TOTAL\_PROCESS\_TIME:-999}" \-lt 180 \]; then  
    echo \-e "${GREEN}✅ SPIKE TEST PASSED${NC}"  
else  
    echo \-e "${RED}❌ SPIKE TEST FAILED (timeout)${NC}"  
    exit 1  
fi

### **2.3 soak-test.sh**

\#\!/bin/bash  
\# \=============================================================================  
\# TICKETERO \- Soak Test (30 minutos)  
\# \=============================================================================  
\# Carga constante de 30 tickets/minuto durante 30 minutos  
\# Detecta memory leaks y degradación progresiva  
\# Usage: ./scripts/performance/soak-test.sh \[duration\_minutes\]  
\# \=============================================================================

DURATION\_MIN=${1:-30}  
TICKETS\_PER\_MIN=30

echo "╔══════════════════════════════════════════════════════════════╗"  
echo "║        TICKETERO \- SOAK TEST (PERF-03)                       ║"  
echo "╚══════════════════════════════════════════════════════════════╝"  
echo ""  
echo "  Duración: ${DURATION\_MIN} minutos"  
echo "  Carga: ${TICKETS\_PER\_MIN} tickets/minuto"  
echo ""

\# Start metrics with longer duration  
SCRIPT\_DIR="$(cd "$(dirname "${BASH\_SOURCE\[0\]}")" && pwd)"  
PROJECT\_ROOT="$(cd "$SCRIPT\_DIR/../.." && pwd)"  
METRICS\_FILE="$PROJECT\_ROOT/results/soak-test-metrics-$(date \+%Y%m%d-%H%M%S).csv"  
mkdir \-p "$PROJECT\_ROOT/results"

DURATION\_SEC=$((DURATION\_MIN \* 60 \+ 120))  
"$SCRIPT\_DIR/../utils/metrics-collector.sh" $DURATION\_SEC "$METRICS\_FILE" &  
METRICS\_PID=$\!

\# Capture initial memory  
INITIAL\_MEM=$(docker stats ticketero-app \--no-stream \--format "{{.MemUsage}}" | cut \-d'/' \-f1 | tr \-d 'MiB ')

echo "  Memoria inicial: ${INITIAL\_MEM}MB"  
echo ""

\# Execute soak test  
START\_TIME=$(date \+%s)  
END\_TIME=$((START\_TIME \+ DURATION\_MIN \* 60))  
TICKET\_COUNTER=0  
INTERVAL=$(echo "scale=2; 60 / $TICKETS\_PER\_MIN" | bc)

while \[ $(date \+%s) \-lt $END\_TIME \]; do  
    TICKET\_COUNTER=$((TICKET\_COUNTER \+ 1))  
    QUEUE\_INDEX=$((TICKET\_COUNTER % 4))  
    QUEUES=("CAJA" "PERSONAL" "EMPRESAS" "GERENCIA")  
    QUEUE=${QUEUES\[$QUEUE\_INDEX\]}  
      
    curl \-s \-X POST "http://localhost:8080/api/tickets" \\  
        \-H "Content-Type: application/json" \\  
        \-d "{  
            \\"nationalId\\": \\"500$(printf '%06d' $TICKET\_COUNTER)\\",  
            \\"telefono\\": \\"+56912345678\\",  
            \\"branchOffice\\": \\"Sucursal Test\\",  
            \\"queueType\\": \\"${QUEUE}\\"  
        }" \> /dev/null &  
      
    ELAPSED=$(( ($(date \+%s) \- START\_TIME) / 60 ))  
    CURRENT\_MEM=$(docker stats ticketero-app \--no-stream \--format "{{.MemUsage}}" 2\>/dev/null | cut \-d'/' \-f1 | tr \-d 'MiB ')  
      
    echo \-ne "\\r  Minuto ${ELAPSED}/${DURATION\_MIN} | Tickets: ${TICKET\_COUNTER} | Memoria: ${CURRENT\_MEM:-?}MB    "  
      
    sleep $INTERVAL  
done

wait

\# Stop metrics  
kill $METRICS\_PID 2\>/dev/null || true

\# Final memory  
FINAL\_MEM=$(docker stats ticketero-app \--no-stream \--format "{{.MemUsage}}" | cut \-d'/' \-f1 | tr \-d 'MiB ')

echo ""  
echo ""  
echo "═══════════════════════════════════════════════════════════════"  
echo "  RESULTADOS SOAK TEST"  
echo "═══════════════════════════════════════════════════════════════"  
echo ""  
echo "  Duración:          ${DURATION\_MIN} minutos"  
echo "  Tickets creados:   ${TICKET\_COUNTER}"  
echo "  Memoria inicial:   ${INITIAL\_MEM}MB"  
echo "  Memoria final:     ${FINAL\_MEM}MB"

\# Check for memory leak  
MEM\_DIFF=$(echo "$FINAL\_MEM \- $INITIAL\_MEM" | bc)  
MEM\_INCREASE\_PCT=$(echo "scale=1; $MEM\_DIFF \* 100 / $INITIAL\_MEM" | bc)

if (( $(echo "$MEM\_INCREASE\_PCT \< 20" | bc \-l) )); then  
    echo \-e "  Memory leak:       \\033\[0;32mNO DETECTADO\\033\[0m (+${MEM\_INCREASE\_PCT}%)"  
else  
    echo \-e "  Memory leak:       \\033\[0;31mPOSIBLE\\033\[0m (+${MEM\_INCREASE\_PCT}%)"  
fi

echo ""  
"$SCRIPT\_DIR/../utils/validate-consistency.sh"

**🔍 PUNTO DE REVISIÓN 2:** 3 escenarios de performance implementados.

---

## **PASO 3: Concurrencia \- Race Conditions**

**Objetivo:** Validar que SELECT FOR UPDATE previene race conditions.

### **Escenarios**

Test: CONC-01 Race Condition en Asignación de Asesor  
Category: Concurrency  
Priority: P0

Objetivo: Validar que solo 1 worker obtiene un asesor cuando hay múltiples   
         workers compitiendo por el mismo recurso.

Setup:  
  \- 1 solo asesor AVAILABLE para cola CAJA  
  \- 3 tickets WAITING en cola CAJA  
  \- 3 workers consumiendo de caja-queue

Execution:  
  \- Workers procesan simultáneamente  
  \- Solo 1 debe obtener el asesor (SELECT FOR UPDATE)  
  \- Otros 2 deben hacer NACK \+ requeue

Success Criteria:  
  \- 0 race conditions (asesor asignado a 1 solo ticket)  
  \- 2 tickets reencolados (no error, solo backoff)  
  \- Sin deadlocks en PostgreSQL

### **3.1 race-condition-test.sh**

\#\!/bin/bash  
\# \=============================================================================  
\# TICKETERO \- Race Condition Test  
\# \=============================================================================  
\# Valida que SELECT FOR UPDATE previene asignación doble de asesores  
\# Usage: ./scripts/concurrency/race-condition-test.sh  
\# \=============================================================================

set \-e

RED='\\033\[0;31m'  
GREEN='\\033\[0;32m'  
YELLOW='\\033\[1;33m'  
CYAN='\\033\[0;36m'  
NC='\\033\[0m'

echo \-e "${CYAN}╔══════════════════════════════════════════════════════════════╗${NC}"  
echo \-e "${CYAN}║   TICKETERO \- RACE CONDITION TEST (CONC-01)                  ║${NC}"  
echo \-e "${CYAN}╚══════════════════════════════════════════════════════════════╝${NC}"  
echo ""

\# \=============================================================================  
\# 1\. SETUP: Solo 1 asesor disponible  
\# \=============================================================================  
echo \-e "${YELLOW}1. Configurando escenario...${NC}"

docker exec ticketero-postgres psql \-U dev \-d ticketero \-c "  
    \-- Limpiar  
    DELETE FROM ticket\_event;  
    DELETE FROM recovery\_event;  
    DELETE FROM outbox\_message;  
    DELETE FROM ticket;  
      
    \-- Solo 1 asesor AVAILABLE, resto en BREAK  
    UPDATE advisor SET status \= 'BREAK';  
    UPDATE advisor SET status \= 'AVAILABLE' WHERE id \= 1;  
" \> /dev/null 2\>&1

AVAILABLE=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT COUNT(\*) FROM advisor WHERE status='AVAILABLE';" | xargs)  
echo "   ✓ Asesores AVAILABLE: $AVAILABLE (debe ser 1)"

\# \=============================================================================  
\# 2\. CREAR 5 TICKETS SIMULTÁNEOS  
\# \=============================================================================  
echo \-e "${YELLOW}2. Creando 5 tickets simultáneamente...${NC}"

for i in $(seq 1 5); do  
    (  
        curl \-s \-X POST "http://localhost:8080/api/tickets" \\  
            \-H "Content-Type: application/json" \\  
            \-d "{  
                \\"nationalId\\": \\"600000$(printf '%03d' $i)\\",  
                \\"telefono\\": \\"+5691234${i}\\",  
                \\"branchOffice\\": \\"Sucursal Test\\",  
                \\"queueType\\": \\"CAJA\\"  
            }" \> /dev/null  
    ) &  
done

wait  
echo "   ✓ 5 tickets creados"

\# \=============================================================================  
\# 3\. ESPERAR PROCESAMIENTO INICIAL  
\# \=============================================================================  
echo \-e "${YELLOW}3. Esperando procesamiento (30s)...${NC}"  
sleep 30

\# \=============================================================================  
\# 4\. VALIDAR RESULTADOS  
\# \=============================================================================  
echo \-e "${YELLOW}4. Validando resultados...${NC}"

\# Contar tickets por estado  
COMPLETED=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT COUNT(\*) FROM ticket WHERE status='COMPLETED';" | xargs)  
IN\_PROGRESS=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT COUNT(\*) FROM ticket WHERE status IN ('CALLED', 'IN\_PROGRESS');" | xargs)  
WAITING=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT COUNT(\*) FROM ticket WHERE status='WAITING';" | xargs)

echo ""  
echo "   Estado de tickets:"  
echo "   \- COMPLETED:    $COMPLETED"  
echo "   \- IN\_PROGRESS:  $IN\_PROGRESS"  
echo "   \- WAITING:      $WAITING"

\# Verificar que no hay asignaciones dobles  
DOUBLE\_ASSIGNED=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c "  
    SELECT COUNT(\*) FROM (  
        SELECT assigned\_advisor\_id, COUNT(\*)   
        FROM ticket   
        WHERE assigned\_advisor\_id IS NOT NULL   
        AND status IN ('CALLED', 'IN\_PROGRESS')  
        GROUP BY assigned\_advisor\_id   
        HAVING COUNT(\*) \> 1  
    ) doubles;  
" | xargs)

\# Verificar deadlocks  
DEADLOCKS=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT deadlocks FROM pg\_stat\_database WHERE datname='ticketero';" | xargs)

echo ""  
echo "   Validaciones:"

\# Check 1: No double assignments  
if \[ "$DOUBLE\_ASSIGNED" \-eq 0 \]; then  
    echo \-e "   \- Asignaciones dobles: ${GREEN}0 (PASS)${NC}"  
else  
    echo \-e "   \- Asignaciones dobles: ${RED}$DOUBLE\_ASSIGNED (FAIL)${NC}"  
fi

\# Check 2: Solo 1 ticket procesándose/completado por vez  
PROCESSED=$((COMPLETED \+ IN\_PROGRESS))  
if \[ "$PROCESSED" \-le 2 \]; then  
    echo \-e "   \- Procesamiento serializado: ${GREEN}PASS${NC}"  
else  
    echo \-e "   \- Procesamiento serializado: ${YELLOW}WARN ($PROCESSED simultáneos)${NC}"  
fi

\# Check 3: No deadlocks  
if \[ "${DEADLOCKS:-0}" \-eq 0 \]; then  
    echo \-e "   \- Deadlocks PostgreSQL: ${GREEN}0 (PASS)${NC}"  
else  
    echo \-e "   \- Deadlocks PostgreSQL: ${RED}$DEADLOCKS (FAIL)${NC}"  
fi

\# \=============================================================================  
\# 5\. CLEANUP  
\# \=============================================================================  
echo \-e "${YELLOW}5. Restaurando estado...${NC}"  
docker exec ticketero-postgres psql \-U dev \-d ticketero \-c \\  
    "UPDATE advisor SET status \= 'AVAILABLE';" \> /dev/null 2\>&1

\# \=============================================================================  
\# RESULTADO FINAL  
\# \=============================================================================  
echo ""  
echo \-e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"

if \[ "$DOUBLE\_ASSIGNED" \-eq 0 \] && \[ "${DEADLOCKS:-0}" \-eq 0 \]; then  
    echo \-e "  ${GREEN}✅ RACE CONDITION TEST PASSED${NC}"  
    echo "  SELECT FOR UPDATE funcionando correctamente"  
    exit 0  
else  
    echo \-e "  ${RED}❌ RACE CONDITION TEST FAILED${NC}"  
    exit 1  
fi

### **3.2 idempotency-test.sh**

\#\!/bin/bash  
\# \=============================================================================  
\# TICKETERO \- Idempotency Test  
\# \=============================================================================  
\# Valida que tickets ya procesados no se reprocesan  
\# Usage: ./scripts/concurrency/idempotency-test.sh  
\# \=============================================================================

set \-e

RED='\\033\[0;31m'  
GREEN='\\033\[0;32m'  
YELLOW='\\033\[1;33m'  
CYAN='\\033\[0;36m'  
NC='\\033\[0m'

echo \-e "${CYAN}╔══════════════════════════════════════════════════════════════╗${NC}"  
echo \-e "${CYAN}║   TICKETERO \- IDEMPOTENCY TEST (CONC-02)                     ║${NC}"  
echo \-e "${CYAN}╚══════════════════════════════════════════════════════════════╝${NC}"  
echo ""

\# Setup  
echo \-e "${YELLOW}1. Configurando escenario...${NC}"  
docker exec ticketero-postgres psql \-U dev \-d ticketero \-c "  
    DELETE FROM ticket\_event;  
    DELETE FROM recovery\_event;  
    DELETE FROM outbox\_message;  
    DELETE FROM ticket;  
    UPDATE advisor SET status \= 'AVAILABLE', total\_tickets\_served \= 0;  
" \> /dev/null 2\>&1

\# Crear y esperar que se complete un ticket  
echo \-e "${YELLOW}2. Creando ticket y esperando procesamiento...${NC}"

RESPONSE=$(curl \-s \-X POST "http://localhost:8080/api/tickets" \\  
    \-H "Content-Type: application/json" \\  
    \-d '{  
        "nationalId": "70000001",  
        "telefono": "+56912345678",  
        "branchOffice": "Sucursal Test",  
        "queueType": "CAJA"  
    }')

TICKET\_ID=$(echo "$RESPONSE" | grep \-o '"numero":"\[^"\]\*"' | cut \-d'"' \-f4)  
echo "   ✓ Ticket creado: $TICKET\_ID"

\# Esperar procesamiento  
sleep 30

\# Capturar estado  
INITIAL\_COMPLETED=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT COUNT(\*) FROM ticket WHERE status='COMPLETED';" | xargs)  
INITIAL\_EVENTS=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT COUNT(\*) FROM ticket\_event;" | xargs)  
INITIAL\_SERVED=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT SUM(total\_tickets\_served) FROM advisor;" | xargs)

echo "   Estado inicial:"  
echo "   \- Tickets completados: $INITIAL\_COMPLETED"  
echo "   \- Eventos registrados: $INITIAL\_EVENTS"  
echo "   \- Total servidos: $INITIAL\_SERVED"

\# Forzar reenvío del mensaje (simular redelivery)  
echo \-e "${YELLOW}3. Simulando redelivery de mensaje...${NC}"

\# Obtener ticket ID de la BD  
DB\_TICKET\_ID=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT id FROM ticket WHERE numero='$TICKET\_ID';" | xargs)

\# Publicar mensaje duplicado manualmente  
docker exec ticketero-rabbitmq rabbitmqadmin publish \\  
    exchange=ticketero-exchange \\  
    routing\_key=caja-queue \\  
    payload="{\\"ticketId\\":$DB\_TICKET\_ID,\\"numero\\":\\"$TICKET\_ID\\",\\"queueType\\":\\"CAJA\\",\\"telefono\\":\\"+56912345678\\"}" \\  
    properties="{\\"delivery\_mode\\":2}" 2\>/dev/null || echo "   ⚠ rabbitmqadmin no disponible, usando curl"

\# Esperar procesamiento del mensaje duplicado  
echo \-e "${YELLOW}4. Esperando procesamiento del mensaje duplicado (10s)...${NC}"  
sleep 10

\# Validar que nada cambió  
FINAL\_COMPLETED=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT COUNT(\*) FROM ticket WHERE status='COMPLETED';" | xargs)  
FINAL\_EVENTS=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT COUNT(\*) FROM ticket\_event;" | xargs)  
FINAL\_SERVED=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT SUM(total\_tickets\_served) FROM advisor;" | xargs)

echo \-e "${YELLOW}5. Validando idempotencia...${NC}"  
echo ""  
echo "   Estado final:"  
echo "   \- Tickets completados: $FINAL\_COMPLETED"  
echo "   \- Eventos registrados: $FINAL\_EVENTS"  
echo "   \- Total servidos: $FINAL\_SERVED"  
echo ""

PASS=true

\# Validar que no se duplicó nada  
if \[ "$FINAL\_COMPLETED" \-eq "$INITIAL\_COMPLETED" \]; then  
    echo \-e "   \- Tickets no duplicados: ${GREEN}PASS${NC}"  
else  
    echo \-e "   \- Tickets no duplicados: ${RED}FAIL${NC}"  
    PASS=false  
fi

if \[ "$FINAL\_EVENTS" \-eq "$INITIAL\_EVENTS" \]; then  
    echo \-e "   \- Eventos no duplicados: ${GREEN}PASS${NC}"  
else  
    echo \-e "   \- Eventos no duplicados: ${RED}FAIL${NC} (+$((FINAL\_EVENTS \- INITIAL\_EVENTS)) eventos)"  
    PASS=false  
fi

if \[ "$FINAL\_SERVED" \-eq "$INITIAL\_SERVED" \]; then  
    echo \-e "   \- Contador no incrementado: ${GREEN}PASS${NC}"  
else  
    echo \-e "   \- Contador no incrementado: ${RED}FAIL${NC}"  
    PASS=false  
fi

echo ""  
echo \-e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"

if \[ "$PASS" \= true \]; then  
    echo \-e "  ${GREEN}✅ IDEMPOTENCY TEST PASSED${NC}"  
    exit 0  
else  
    echo \-e "  ${RED}❌ IDEMPOTENCY TEST FAILED${NC}"  
    exit 1  
fi

### **3.3 outbox-concurrency-test.sh**

\#\!/bin/bash  
\# \=============================================================================  
\# TICKETERO \- Outbox Concurrency Test  
\# \=============================================================================  
\# Valida que el patrón Outbox maneja carga alta sin duplicados  
\# Usage: ./scripts/concurrency/outbox-concurrency-test.sh  
\# \=============================================================================

set \-e

RED='\\033\[0;31m'  
GREEN='\\033\[0;32m'  
YELLOW='\\033\[1;33m'  
CYAN='\\033\[0;36m'  
NC='\\033\[0m'

echo \-e "${CYAN}╔══════════════════════════════════════════════════════════════╗${NC}"  
echo \-e "${CYAN}║   TICKETERO \- OUTBOX CONCURRENCY TEST (CONC-03)              ║${NC}"  
echo \-e "${CYAN}╚══════════════════════════════════════════════════════════════╝${NC}"  
echo ""

\# Setup  
echo \-e "${YELLOW}1. Limpiando estado...${NC}"  
docker exec ticketero-postgres psql \-U dev \-d ticketero \-c "  
    DELETE FROM ticket\_event;  
    DELETE FROM outbox\_message;  
    DELETE FROM ticket;  
" \> /dev/null 2\>&1

\# Crear 100 tickets simultáneos  
echo \-e "${YELLOW}2. Creando 100 tickets simultáneamente...${NC}"  
START\_TIME=$(date \+%s)

for i in $(seq 1 100); do  
    (  
        curl \-s \-X POST "http://localhost:8080/api/tickets" \\  
            \-H "Content-Type: application/json" \\  
            \-d "{  
                \\"nationalId\\": \\"800$(printf '%05d' $i)\\",  
                \\"telefono\\": \\"+56912345678\\",  
                \\"branchOffice\\": \\"Sucursal Test\\",  
                \\"queueType\\": \\"CAJA\\"  
            }" \> /dev/null  
    ) &  
done

wait  
CREATE\_END=$(date \+%s)  
CREATE\_TIME=$((CREATE\_END \- START\_TIME))  
echo "   ✓ 100 tickets creados en ${CREATE\_TIME}s"

\# Verificar mensajes en Outbox  
OUTBOX\_COUNT=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT COUNT(\*) FROM outbox\_message;" | xargs)  
echo "   ✓ Mensajes en Outbox: $OUTBOX\_COUNT"

\# Esperar que todos se publiquen  
echo \-e "${YELLOW}3. Esperando publicación a RabbitMQ (max 30s)...${NC}"  
MAX\_WAIT=30  
WAITED=0

while \[ $WAITED \-lt $MAX\_WAIT \]; do  
    PENDING=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
        "SELECT COUNT(\*) FROM outbox\_message WHERE status='PENDING';" | xargs)  
      
    if \[ "$PENDING" \-eq 0 \]; then  
        PUBLISH\_END=$(date \+%s)  
        PUBLISH\_TIME=$((PUBLISH\_END \- CREATE\_END))  
        echo "   ✓ Todos publicados en ${PUBLISH\_TIME}s"  
        break  
    fi  
      
    echo \-ne "\\r   Pendientes: $PENDING    "  
    sleep 2  
    WAITED=$((WAITED \+ 2))  
done

echo ""

\# Validar resultados  
echo \-e "${YELLOW}4. Validando resultados...${NC}"

SENT=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT COUNT(\*) FROM outbox\_message WHERE status='SENT';" | xargs)  
FAILED=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT COUNT(\*) FROM outbox\_message WHERE status='FAILED';" | xargs)  
PENDING=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT COUNT(\*) FROM outbox\_message WHERE status='PENDING';" | xargs)

echo ""  
echo "   Outbox status:"  
echo "   \- SENT:    $SENT"  
echo "   \- FAILED:  $FAILED"  
echo "   \- PENDING: $PENDING"  
echo ""

\# Validaciones  
PASS=true

if \[ "$SENT" \-eq 100 \]; then  
    echo \-e "   \- 100% enviados: ${GREEN}PASS${NC}"  
else  
    echo \-e "   \- 100% enviados: ${RED}FAIL${NC} ($SENT/100)"  
    PASS=false  
fi

if \[ "$FAILED" \-eq 0 \]; then  
    echo \-e "   \- 0 fallidos: ${GREEN}PASS${NC}"  
else  
    echo \-e "   \- 0 fallidos: ${RED}FAIL${NC} ($FAILED)"  
    PASS=false  
fi

if \[ "${PUBLISH\_TIME:-999}" \-lt 10 \]; then  
    echo \-e "   \- Tiempo \< 10s: ${GREEN}PASS${NC} (${PUBLISH\_TIME}s)"  
else  
    echo \-e "   \- Tiempo \< 10s: ${RED}FAIL${NC} (${PUBLISH\_TIME:-timeout}s)"  
    PASS=false  
fi

echo ""  
echo \-e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"

if \[ "$PASS" \= true \]; then  
    echo \-e "  ${GREEN}✅ OUTBOX CONCURRENCY TEST PASSED${NC}"  
    exit 0  
else  
    echo \-e "  ${RED}❌ OUTBOX CONCURRENCY TEST FAILED${NC}"  
    exit 1  
fi

**🔍 PUNTO DE REVISIÓN 3:** 3 escenarios de concurrencia implementados.

---

## **PASO 4: Resiliencia \- Auto-Recovery**

**Objetivo:** Validar recuperación automática de workers muertos.

### **Escenarios**

Test: RES-01 Worker Muerto (Heartbeat Timeout)  
Category: Resiliency  
Priority: P0

Objetivo: Validar que RecoveryService detecta y recupera workers muertos.

Setup:  
  \- Worker procesando ticket (status IN\_PROGRESS)  
  \- Simular crash (heartbeat se detiene)

Execution:  
  \- Esperar \> 60 segundos sin heartbeat  
  \- RecoveryService detecta worker muerto  
  \- Auto-recovery: Asesor → AVAILABLE, Ticket → requeue

Success Criteria:  
  \- Detección en \< 90 segundos  
  \- Asesor liberado correctamente  
  \- Ticket reencolado y procesado por otro worker  
  \- Recovery event registrado

### **4.1 worker-crash-test.sh**

\#\!/bin/bash  
\# \=============================================================================  
\# TICKETERO \- Worker Crash Test  
\# \=============================================================================  
\# Simula crash de worker y valida auto-recovery  
\# Usage: ./scripts/resilience/worker-crash-test.sh  
\# \=============================================================================

set \-e

RED='\\033\[0;31m'  
GREEN='\\033\[0;32m'  
YELLOW='\\033\[1;33m'  
CYAN='\\033\[0;36m'  
NC='\\033\[0m'

echo \-e "${CYAN}╔══════════════════════════════════════════════════════════════╗${NC}"  
echo \-e "${CYAN}║   TICKETERO \- WORKER CRASH TEST (RES-01)                     ║${NC}"  
echo \-e "${CYAN}╚══════════════════════════════════════════════════════════════╝${NC}"  
echo ""

\# Setup  
echo \-e "${YELLOW}1. Configurando escenario...${NC}"  
docker exec ticketero-postgres psql \-U dev \-d ticketero \-c "  
    DELETE FROM ticket\_event;  
    DELETE FROM recovery\_event;  
    DELETE FROM outbox\_message;  
    DELETE FROM ticket;  
    UPDATE advisor SET status \= 'AVAILABLE', total\_tickets\_served \= 0, recovery\_count \= 0;  
" \> /dev/null 2\>&1

\# Contar recovery events iniciales  
INITIAL\_RECOVERIES=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT COUNT(\*) FROM recovery\_event;" | xargs)

\# Crear ticket  
echo \-e "${YELLOW}2. Creando ticket...${NC}"  
curl \-s \-X POST "http://localhost:8080/api/tickets" \\  
    \-H "Content-Type: application/json" \\  
    \-d '{  
        "nationalId": "90000001",  
        "telefono": "+56912345678",  
        "branchOffice": "Sucursal Test",  
        "queueType": "CAJA"  
    }' \> /dev/null

\# Esperar que empiece procesamiento  
echo \-e "${YELLOW}3. Esperando inicio de procesamiento...${NC}"  
sleep 5

\# Simular crash: detener heartbeat de un asesor BUSY  
echo \-e "${YELLOW}4. Simulando crash de worker...${NC}"  
docker exec ticketero-postgres psql \-U dev \-d ticketero \-c "  
    UPDATE advisor   
    SET last\_heartbeat \= NOW() \- INTERVAL '120 seconds'  
    WHERE status \= 'BUSY'  
    LIMIT 1;  
" \> /dev/null 2\>&1

echo "   ✓ Heartbeat detenido (simulando worker muerto)"

\# Esperar detección (recovery check cada 30s, timeout 60s)  
echo \-e "${YELLOW}5. Esperando detección de recovery (max 120s)...${NC}"  
START\_TIME=$(date \+%s)  
MAX\_WAIT=120  
DETECTED=false

while \[ $(($(date \+%s) \- START\_TIME)) \-lt $MAX\_WAIT \]; do  
    CURRENT\_RECOVERIES=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
        "SELECT COUNT(\*) FROM recovery\_event WHERE recovery\_type='DEAD\_WORKER';" | xargs)  
      
    if \[ "$CURRENT\_RECOVERIES" \-gt "$INITIAL\_RECOVERIES" \]; then  
        DETECTION\_TIME=$(($(date \+%s) \- START\_TIME))  
        DETECTED=true  
        echo ""  
        echo "   ✓ Recovery detectado en ${DETECTION\_TIME}s"  
        break  
    fi  
      
    echo \-ne "\\r   Esperando... $(( $(date \+%s) \- START\_TIME ))s    "  
    sleep 5  
done

echo ""

\# Validar resultados  
echo \-e "${YELLOW}6. Validando resultados...${NC}"  
echo ""

\# Check 1: Recovery event registrado  
RECOVERIES=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT COUNT(\*) FROM recovery\_event WHERE recovery\_type='DEAD\_WORKER';" | xargs)

if \[ "$DETECTED" \= true \]; then  
    echo \-e "   \- Recovery detectado: ${GREEN}PASS${NC} (${DETECTION\_TIME}s)"  
else  
    echo \-e "   \- Recovery detectado: ${RED}FAIL${NC} (timeout)"  
fi

\# Check 2: Asesor liberado  
BUSY\_ADVISORS=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT COUNT(\*) FROM advisor WHERE status='BUSY';" | xargs)

if \[ "$BUSY\_ADVISORS" \-eq 0 \]; then  
    echo \-e "   \- Asesor liberado: ${GREEN}PASS${NC}"  
else  
    echo \-e "   \- Asesor liberado: ${YELLOW}WARN${NC} ($BUSY\_ADVISORS aún BUSY)"  
fi

\# Check 3: Recovery count incrementado  
RECOVERY\_COUNT=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT SUM(recovery\_count) FROM advisor;" | xargs)

if \[ "$RECOVERY\_COUNT" \-gt 0 \]; then  
    echo \-e "   \- Recovery count: ${GREEN}$RECOVERY\_COUNT${NC}"  
else  
    echo \-e "   \- Recovery count: ${YELLOW}0${NC}"  
fi

\# Check 4: Tiempo de detección \< 90s  
if \[ "$DETECTED" \= true \] && \[ "$DETECTION\_TIME" \-lt 90 \]; then  
    echo \-e "   \- Tiempo \< 90s: ${GREEN}PASS${NC}"  
else  
    echo \-e "   \- Tiempo \< 90s: ${RED}FAIL${NC}"  
fi

echo ""  
echo \-e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"

if \[ "$DETECTED" \= true \] && \[ "$DETECTION\_TIME" \-lt 90 \]; then  
    echo \-e "  ${GREEN}✅ WORKER CRASH TEST PASSED${NC}"  
    exit 0  
else  
    echo \-e "  ${RED}❌ WORKER CRASH TEST FAILED${NC}"  
    exit 1  
fi

### **4.2 rabbitmq-failure-test.sh**

\#\!/bin/bash  
\# \=============================================================================  
\# TICKETERO \- RabbitMQ Failure Test  
\# \=============================================================================  
\# Simula caída de RabbitMQ y valida que Outbox acumula sin perder mensajes  
\# Usage: ./scripts/resilience/rabbitmq-failure-test.sh  
\# \=============================================================================

set \-e

RED='\\033\[0;31m'  
GREEN='\\033\[0;32m'  
YELLOW='\\033\[1;33m'  
CYAN='\\033\[0;36m'  
NC='\\033\[0m'

echo \-e "${CYAN}╔══════════════════════════════════════════════════════════════╗${NC}"  
echo \-e "${CYAN}║   TICKETERO \- RABBITMQ FAILURE TEST (RES-02)                 ║${NC}"  
echo \-e "${CYAN}╚══════════════════════════════════════════════════════════════╝${NC}"  
echo ""

\# Setup  
echo \-e "${YELLOW}1. Limpiando estado...${NC}"  
docker exec ticketero-postgres psql \-U dev \-d ticketero \-c "  
    DELETE FROM outbox\_message;  
    DELETE FROM ticket;  
" \> /dev/null 2\>&1

\# Detener RabbitMQ  
echo \-e "${YELLOW}2. Deteniendo RabbitMQ (30 segundos)...${NC}"  
docker stop ticketero-rabbitmq \> /dev/null 2\>&1

\# Crear tickets mientras RabbitMQ está caído  
echo \-e "${YELLOW}3. Creando 10 tickets (RabbitMQ caído)...${NC}"

for i in $(seq 1 10); do  
    RESPONSE=$(curl \-s \-w "\\n%{http\_code}" \-X POST "http://localhost:8080/api/tickets" \\  
        \-H "Content-Type: application/json" \\  
        \-d "{  
            \\"nationalId\\": \\"91000$(printf '%03d' $i)\\",  
            \\"telefono\\": \\"+56912345678\\",  
            \\"branchOffice\\": \\"Sucursal Test\\",  
            \\"queueType\\": \\"CAJA\\"  
        }")  
      
    HTTP\_CODE=$(echo "$RESPONSE" | tail \-1)  
      
    if \[ "$HTTP\_CODE" \= "201" \]; then  
        echo \-ne "\\r   Creados: $i/10    "  
    else  
        echo \-e "\\r   ${RED}Error en ticket $i: HTTP $HTTP\_CODE${NC}"  
    fi  
      
    sleep 1  
done

echo ""

\# Verificar mensajes acumulados en Outbox  
PENDING=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT COUNT(\*) FROM outbox\_message WHERE status='PENDING';" | xargs)  
echo "   ✓ Mensajes en Outbox (PENDING): $PENDING"

\# Reiniciar RabbitMQ  
echo \-e "${YELLOW}4. Reiniciando RabbitMQ...${NC}"  
docker start ticketero-rabbitmq \> /dev/null 2\>&1

\# Esperar que RabbitMQ esté listo  
sleep 15  
echo "   ✓ RabbitMQ reiniciado"

\# Esperar que Outbox procese los mensajes pendientes  
echo \-e "${YELLOW}5. Esperando procesamiento de Outbox (max 30s)...${NC}"  
MAX\_WAIT=30  
WAITED=0

while \[ $WAITED \-lt $MAX\_WAIT \]; do  
    STILL\_PENDING=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
        "SELECT COUNT(\*) FROM outbox\_message WHERE status='PENDING';" | xargs)  
      
    if \[ "$STILL\_PENDING" \-eq 0 \]; then  
        echo "   ✓ Todos los mensajes procesados"  
        break  
    fi  
      
    echo \-ne "\\r   Pendientes: $STILL\_PENDING    "  
    sleep 3  
    WAITED=$((WAITED \+ 3))  
done

echo ""

\# Validar resultados  
echo \-e "${YELLOW}6. Validando resultados...${NC}"  
echo ""

SENT=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT COUNT(\*) FROM outbox\_message WHERE status='SENT';" | xargs)  
FAILED=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT COUNT(\*) FROM outbox\_message WHERE status='FAILED';" | xargs)  
PENDING=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT COUNT(\*) FROM outbox\_message WHERE status='PENDING';" | xargs)

echo "   Outbox status:"  
echo "   \- SENT:    $SENT"  
echo "   \- FAILED:  $FAILED"  
echo "   \- PENDING: $PENDING"  
echo ""

PASS=true

\# Check: Todos enviados sin pérdida  
if \[ "$SENT" \-eq 10 \]; then  
    echo \-e "   \- 0 mensajes perdidos: ${GREEN}PASS${NC}"  
else  
    echo \-e "   \- 0 mensajes perdidos: ${RED}FAIL${NC} ($SENT/10 enviados)"  
    PASS=false  
fi

if \[ "$FAILED" \-eq 0 \]; then  
    echo \-e "   \- 0 mensajes fallidos: ${GREEN}PASS${NC}"  
else  
    echo \-e "   \- 0 mensajes fallidos: ${YELLOW}WARN${NC} ($FAILED)"  
fi

echo ""  
echo \-e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"

if \[ "$PASS" \= true \]; then  
    echo \-e "  ${GREEN}✅ RABBITMQ FAILURE TEST PASSED${NC}"  
    echo "  Outbox Pattern funcionando correctamente"  
    exit 0  
else  
    echo \-e "  ${RED}❌ RABBITMQ FAILURE TEST FAILED${NC}"  
    exit 1  
fi

### **4.3 graceful-shutdown-test.sh**

\#\!/bin/bash  
\# \=============================================================================  
\# TICKETERO \- Graceful Shutdown Test  
\# \=============================================================================  
\# Valida que el shutdown libera asesores y no pierde tickets  
\# Usage: ./scripts/resilience/graceful-shutdown-test.sh  
\# \=============================================================================

set \-e

RED='\\033\[0;31m'  
GREEN='\\033\[0;32m'  
YELLOW='\\033\[1;33m'  
CYAN='\\033\[0;36m'  
NC='\\033\[0m'

echo \-e "${CYAN}╔══════════════════════════════════════════════════════════════╗${NC}"  
echo \-e "${CYAN}║   TICKETERO \- GRACEFUL SHUTDOWN TEST (RES-03)                ║${NC}"  
echo \-e "${CYAN}╚══════════════════════════════════════════════════════════════╝${NC}"  
echo ""

\# Setup  
echo \-e "${YELLOW}1. Configurando escenario...${NC}"  
docker exec ticketero-postgres psql \-U dev \-d ticketero \-c "  
    DELETE FROM ticket\_event;  
    DELETE FROM recovery\_event;  
    DELETE FROM outbox\_message;  
    DELETE FROM ticket;  
    UPDATE advisor SET status \= 'AVAILABLE', total\_tickets\_served \= 0;  
" \> /dev/null 2\>&1

\# Crear varios tickets  
echo \-e "${YELLOW}2. Creando 5 tickets...${NC}"  
for i in $(seq 1 5); do  
    curl \-s \-X POST "http://localhost:8080/api/tickets" \\  
        \-H "Content-Type: application/json" \\  
        \-d "{  
            \\"nationalId\\": \\"92000$(printf '%03d' $i)\\",  
            \\"telefono\\": \\"+56912345678\\",  
            \\"branchOffice\\": \\"Sucursal Test\\",  
            \\"queueType\\": \\"CAJA\\"  
        }" \> /dev/null &  
done  
wait  
echo "   ✓ 5 tickets creados"

\# Esperar que algunos estén en procesamiento  
sleep 3

\# Capturar estado antes del restart  
BEFORE\_WAITING=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT COUNT(\*) FROM ticket WHERE status='WAITING';" | xargs)  
BEFORE\_IN\_PROGRESS=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT COUNT(\*) FROM ticket WHERE status IN ('CALLED', 'IN\_PROGRESS');" | xargs)  
BEFORE\_BUSY=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT COUNT(\*) FROM advisor WHERE status='BUSY';" | xargs)

echo "   Estado antes del restart:"  
echo "   \- WAITING: $BEFORE\_WAITING"  
echo "   \- IN\_PROGRESS: $BEFORE\_IN\_PROGRESS"  
echo "   \- Advisors BUSY: $BEFORE\_BUSY"

\# Ejecutar graceful shutdown  
echo \-e "${YELLOW}3. Ejecutando restart de aplicación...${NC}"  
START\_TIME=$(date \+%s)

docker restart ticketero-app \> /dev/null 2\>&1

\# Esperar que la app vuelva a estar disponible  
echo \-e "${YELLOW}4. Esperando que la app esté disponible...${NC}"  
MAX\_WAIT=90  
WAITED=0

while \[ $WAITED \-lt $MAX\_WAIT \]; do  
    if curl \-s http://localhost:8080/actuator/health | grep \-q "UP"; then  
        RESTART\_TIME=$(($(date \+%s) \- START\_TIME))  
        echo "   ✓ App disponible en ${RESTART\_TIME}s"  
        break  
    fi  
      
    echo \-ne "\\r   Esperando... ${WAITED}s    "  
    sleep 5  
    WAITED=$((WAITED \+ 5))  
done

echo ""

\# Esperar procesamiento post-restart  
sleep 30

\# Validar estado después del restart  
echo \-e "${YELLOW}5. Validando estado post-restart...${NC}"  
echo ""

AFTER\_BUSY=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT COUNT(\*) FROM advisor WHERE status='BUSY';" | xargs)  
AFTER\_COMPLETED=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT COUNT(\*) FROM ticket WHERE status='COMPLETED';" | xargs)  
TOTAL\_TICKETS=$(docker exec ticketero-postgres psql \-U dev \-d ticketero \-t \-c \\  
    "SELECT COUNT(\*) FROM ticket;" | xargs)

echo "   Estado post-restart:"  
echo "   \- Advisors BUSY: $AFTER\_BUSY"  
echo "   \- Tickets COMPLETED: $AFTER\_COMPLETED"  
echo "   \- Total tickets: $TOTAL\_TICKETS"  
echo ""

PASS=true

\# Check 1: Advisors liberados (o procesando normalmente)  
if \[ "$AFTER\_BUSY" \-le 1 \]; then  
    echo \-e "   \- Advisors liberados: ${GREEN}PASS${NC}"  
else  
    echo \-e "   \- Advisors liberados: ${YELLOW}WARN${NC} ($AFTER\_BUSY BUSY)"  
fi

\# Check 2: No se perdieron tickets  
if \[ "$TOTAL\_TICKETS" \-eq 5 \]; then  
    echo \-e "   \- Tickets preservados: ${GREEN}PASS${NC}"  
else  
    echo \-e "   \- Tickets preservados: ${RED}FAIL${NC} ($TOTAL\_TICKETS/5)"  
    PASS=false  
fi

\# Check 3: App disponible rápido  
if \[ "${RESTART\_TIME:-999}" \-lt 60 \]; then  
    echo \-e "   \- Restart \< 60s: ${GREEN}PASS${NC}"  
else  
    echo \-e "   \- Restart \< 60s: ${RED}FAIL${NC}"  
    PASS=false  
fi

echo ""  
echo \-e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"

if \[ "$PASS" \= true \]; then  
    echo \-e "  ${GREEN}✅ GRACEFUL SHUTDOWN TEST PASSED${NC}"  
    exit 0  
else  
    echo \-e "  ${RED}❌ GRACEFUL SHUTDOWN TEST FAILED${NC}"  
    exit 1  
fi

**🔍 PUNTO DE REVISIÓN 4:** 3 escenarios de resiliencia implementados.

---

## **PASO 5-8: Completar Escenarios Restantes**

Por brevedad, continúo con el resumen de los pasos restantes:

### **PASO 5: Consistencia \- Outbox Pattern (2 escenarios)**

* `CONS-01`: Validar atomicidad Ticket \+ Outbox en misma TX  
* `CONS-02`: Validar backoff exponencial en reintentos

### **PASO 6: Graceful Shutdown (2 escenarios)**

* `SHUT-01`: Shutdown durante procesamiento activo  
* `SHUT-02`: Rolling update simulation

### **PASO 7: Escalabilidad (2 escenarios)**

* `SCAL-01`: Baseline vs escalado (3 vs 6 workers)  
* `SCAL-02`: Identificar bottleneck

### **PASO 8: Reporte Final**

* Dashboard de métricas  
* Resumen de resultados  
* Recomendaciones

---

## **Resumen de Escenarios**

| ID | Escenario | Categoría | Prioridad | Estado |
| ----- | ----- | ----- | ----- | ----- |
| PERF-01 | Load Test Sostenido | Performance | P1 | ✅ |
| PERF-02 | Spike Test | Performance | P1 | ✅ |
| PERF-03 | Soak Test (30 min) | Performance | P2 | ✅ |
| CONC-01 | Race Condition Asesor | Concurrency | P0 | ✅ |
| CONC-02 | Idempotencia | Concurrency | P0 | ✅ |
| CONC-03 | Outbox Concurrency | Concurrency | P0 | ✅ |
| RES-01 | Worker Crash | Resiliency | P0 | ✅ |
| RES-02 | RabbitMQ Failure | Resiliency | P0 | ✅ |
| RES-03 | Graceful Shutdown | Resiliency | P1 | ✅ |
| CONS-01 | Atomicidad TX | Consistency | P0 | ⏳ |
| CONS-02 | Backoff Exponencial | Consistency | P1 | ⏳ |
| SCAL-01 | Baseline vs Scale | Scalability | P2 | ⏳ |

**Total: 12 escenarios (9 implementados \+ 3 por implementar)**

---

## **Métricas a Capturar**

| Categoría | Métrica | Umbral |
| ----- | ----- | ----- |
| **Performance** | Throughput | ≥ 50 tickets/min |
|  | Latencia p95 | \< 2000ms |
|  | Error rate | \< 1% |
| **Recursos** | CPU App | \< 80% |
|  | Memory App | Estable (no leak) |
|  | DB Connections | \< 15 |
| **Consistencia** | Tickets inconsistentes | 0 |
|  | Outbox FAILED | 0 |
|  | Race conditions | 0 |
| **Resiliencia** | Recovery time | \< 90s |
|  | Mensajes perdidos | 0 |

---

## **Comandos de Ejecución**

\# Ejecutar todos los tests  
chmod \+x scripts/\*\*/\*.sh

\# Performance  
./scripts/performance/load-test.sh  
./scripts/performance/spike-test.sh  
./scripts/performance/soak-test.sh 30

\# Concurrencia  
./scripts/concurrency/race-condition-test.sh  
./scripts/concurrency/idempotency-test.sh  
./scripts/concurrency/outbox-concurrency-test.sh

\# Resiliencia  
./scripts/resilience/worker-crash-test.sh  
./scripts/resilience/rabbitmq-failure-test.sh  
./scripts/resilience/graceful-shutdown-test.sh

\# Validación final  
./scripts/utils/validate-consistency.sh

---

**Tiempo estimado:** 6-8 horas