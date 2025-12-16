#!/bin/bash
# Load Test Sostenido: 50+ tickets/minuto durante 2 minutos
# Usage: ./scripts/performance/load-test.sh

set -e

CYAN='\033[0;36m'
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${CYAN}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║        TICKETERO - LOAD TEST SOSTENIDO (PERF-01)             ║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════════════════════════╝${NC}"

# Cleanup
echo -e "${YELLOW}1. Limpiando estado previo...${NC}"
docker exec ticketero-postgres psql -U dev -d ticketero -c "
    DELETE FROM ticket_event;
    DELETE FROM outbox_message;
    DELETE FROM ticket;
    UPDATE advisor SET status = 'AVAILABLE', total_tickets_served = 0;
" > /dev/null 2>&1

# Start metrics collection
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
METRICS_FILE="$PROJECT_ROOT/results/load-test-metrics-$(date +%Y%m%d-%H%M%S).csv"
mkdir -p "$PROJECT_ROOT/results"

"$SCRIPT_DIR/../utils/metrics-collector.sh" 150 "$METRICS_FILE" &
METRICS_PID=$!

# Execute load test
echo -e "${YELLOW}2. Ejecutando load test (2 minutos)...${NC}"
START_TIME=$(date +%s)

if command -v k6 &> /dev/null; then
    k6 run --vus 10 --duration 2m "$PROJECT_ROOT/k6/load-test.js" \
        --out json="$PROJECT_ROOT/results/load-test-k6.json"
else
    # Fallback: bash implementation
    for i in $(seq 1 100); do
        QUEUE_INDEX=$((i % 4))
        QUEUES=("CAJA" "PERSONAL" "EMPRESAS" "GERENCIA")
        QUEUE=${QUEUES[$QUEUE_INDEX]}
        
        curl -s -X POST "http://localhost:8080/api/tickets" \
            -H "Content-Type: application/json" \
            -d "{
                \"nationalId\": \"300000$(printf '%03d' $i)\",
                \"telefono\": \"+5691234${i}\",
                \"branchOffice\": \"Sucursal Test\",
                \"queueType\": \"${QUEUE}\"
            }" > /dev/null &
        
        sleep 1.2  # ~50 tickets/min
    done
    wait
fi

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

# Wait for processing
echo -e "${YELLOW}3. Esperando procesamiento completo...${NC}"
sleep 60

# Stop metrics
kill $METRICS_PID 2>/dev/null || true

# Collect results
TOTAL_TICKETS=$(docker exec ticketero-postgres psql -U dev -d ticketero -t -c \
    "SELECT COUNT(*) FROM ticket;" | xargs)
COMPLETED_TICKETS=$(docker exec ticketero-postgres psql -U dev -d ticketero -t -c \
    "SELECT COUNT(*) FROM ticket WHERE status='COMPLETED';" | xargs)

THROUGHPUT=$(echo "scale=1; $COMPLETED_TICKETS * 60 / $DURATION" | bc)

# Validate consistency
"$SCRIPT_DIR/../utils/validate-consistency.sh"
CONSISTENCY_RESULT=$?

# Results
echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}  RESULTADOS LOAD TEST SOSTENIDO${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
echo ""
echo "  Duración:           ${DURATION} segundos"
echo "  Tickets creados:    ${TOTAL_TICKETS}"
echo "  Tickets completados: ${COMPLETED_TICKETS}"
echo ""

# Throughput check
if (( $(echo "$THROUGHPUT >= 50" | bc -l) )); then
    echo -e "  Throughput:         ${GREEN}${THROUGHPUT} tickets/min${NC} (≥50 ✓)"
else
    echo -e "  Throughput:         ${RED}${THROUGHPUT} tickets/min${NC} (<50 ✗)"
fi

# Consistency check
if [ $CONSISTENCY_RESULT -eq 0 ]; then
    echo -e "  Consistencia:       ${GREEN}PASS${NC}"
else
    echo -e "  Consistencia:       ${RED}FAIL${NC}"
fi

echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"

# Exit code
if (( $(echo "$THROUGHPUT >= 50" | bc -l) )) && [ $CONSISTENCY_RESULT -eq 0 ]; then
    echo -e "${GREEN}✅ LOAD TEST PASSED${NC}"
    exit 0
else
    echo -e "${RED}❌ LOAD TEST FAILED${NC}"
    exit 1
fi