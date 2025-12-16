#!/bin/bash
# Soak Test: Carga sostenida durante 30 minutos para detectar memory leaks
# Usage: ./scripts/performance/soak-test.sh

set -e

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║        TICKETERO - SOAK TEST (PERF-03)                       ║"
echo "╚══════════════════════════════════════════════════════════════╝"

# Cleanup
docker exec ticketero-postgres psql -U dev -d ticketero -c "
    DELETE FROM ticket_event; DELETE FROM outbox_message; DELETE FROM ticket;
    UPDATE advisor SET status = 'AVAILABLE';
" > /dev/null 2>&1

# Start metrics collection for 30 minutes
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
METRICS_FILE="$PROJECT_ROOT/results/soak-test-metrics-$(date +%Y%m%d-%H%M%S).csv"

"$SCRIPT_DIR/../utils/metrics-collector.sh" 1800 "$METRICS_FILE" &
METRICS_PID=$!

# Execute soak test (30 minutes)
echo "Ejecutando soak test (30 minutos)..."
START_TIME=$(date +%s)
END_TIME=$((START_TIME + 1800))  # 30 minutes

TICKET_COUNTER=0
while [ $(date +%s) -lt $END_TIME ]; do
    for i in $(seq 1 5); do
        TICKET_COUNTER=$((TICKET_COUNTER + 1))
        QUEUE_INDEX=$((TICKET_COUNTER % 4))
        QUEUES=("CAJA" "PERSONAL" "EMPRESAS" "GERENCIA")
        QUEUE=${QUEUES[$QUEUE_INDEX]}
        
        curl -s -X POST "http://localhost:8080/api/tickets" \
            -H "Content-Type: application/json" \
            -d "{
                \"nationalId\": \"500$(printf '%05d' $TICKET_COUNTER)\",
                \"telefono\": \"+56912345678\",
                \"branchOffice\": \"Sucursal Test\",
                \"queueType\": \"${QUEUE}\"
            }" > /dev/null &
    done
    
    sleep 10  # ~30 tickets/min sustained
done

wait
kill $METRICS_PID 2>/dev/null || true

# Analyze memory usage
INITIAL_MEM=$(head -2 "$METRICS_FILE" | tail -1 | cut -d',' -f3)
FINAL_MEM=$(tail -1 "$METRICS_FILE" | cut -d',' -f3)
MEM_INCREASE=$((FINAL_MEM - INITIAL_MEM))

TOTAL_TICKETS=$(docker exec ticketero-postgres psql -U dev -d ticketero -t -c \
    "SELECT COUNT(*) FROM ticket;" | xargs)

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "  RESULTADOS SOAK TEST"
echo "═══════════════════════════════════════════════════════════════"
echo "  Duración:           30 minutos"
echo "  Tickets creados:    ${TOTAL_TICKETS}"
echo "  Memoria inicial:    ${INITIAL_MEM} MB"
echo "  Memoria final:      ${FINAL_MEM} MB"
echo "  Incremento memoria: ${MEM_INCREASE} MB"

if [ "$MEM_INCREASE" -lt 100 ]; then
    echo "✅ SOAK TEST PASSED (Memory leak < 100MB)"
    exit 0
else
    echo "❌ SOAK TEST FAILED (Memory leak ≥ 100MB)"
    exit 1
fi