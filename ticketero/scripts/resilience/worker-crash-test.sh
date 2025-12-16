#!/bin/bash
# Worker Crash Test: Valida auto-recovery
# Usage: ./scripts/resilience/worker-crash-test.sh

set -e

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║   TICKETERO - WORKER CRASH TEST (RES-01)                     ║"
echo "╚══════════════════════════════════════════════════════════════╝"

# Setup
docker exec ticketero-postgres psql -U dev -d ticketero -c "
    DELETE FROM ticket_event; DELETE FROM recovery_event; DELETE FROM outbox_message; DELETE FROM ticket;
    UPDATE advisor SET status = 'AVAILABLE', recovery_count = 0;
" > /dev/null 2>&1

# Crear ticket
curl -s -X POST "http://localhost:8080/api/tickets" \
    -H "Content-Type: application/json" \
    -d '{
        "nationalId": "90000001",
        "telefono": "+56912345678",
        "branchOffice": "Sucursal Test",
        "queueType": "CAJA"
    }' > /dev/null

# Esperar que empiece procesamiento
sleep 5

# Simular crash: detener heartbeat
docker exec ticketero-postgres psql -U dev -d ticketero -c "
    UPDATE advisor 
    SET last_heartbeat = NOW() - INTERVAL '120 seconds'
    WHERE status = 'BUSY'
    LIMIT 1;
" > /dev/null 2>&1

echo "Worker crash simulado (heartbeat detenido)"

# Esperar detección de recovery (max 120s)
echo "Esperando detección de recovery..."
START_TIME=$(date +%s)
DETECTED=false

while [ $(($(date +%s) - START_TIME)) -lt 120 ]; do
    RECOVERIES=$(docker exec ticketero-postgres psql -U dev -d ticketero -t -c \
        "SELECT COUNT(*) FROM recovery_event WHERE recovery_type='DEAD_WORKER';" | xargs)
    
    if [ "$RECOVERIES" -gt 0 ]; then
        DETECTION_TIME=$(($(date +%s) - START_TIME))
        DETECTED=true
        break
    fi
    
    sleep 5
done

# Validar resultados
BUSY_ADVISORS=$(docker exec ticketero-postgres psql -U dev -d ticketero -t -c \
    "SELECT COUNT(*) FROM advisor WHERE status='BUSY';" | xargs)

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "  RESULTADOS WORKER CRASH TEST"
echo "═══════════════════════════════════════════════════════════════"

if [ "$DETECTED" = true ] && [ "$DETECTION_TIME" -lt 90 ]; then
    echo "✅ WORKER CRASH TEST PASSED"
    echo "  - Recovery detectado: ${DETECTION_TIME}s (< 90s)"
    echo "  - Asesores BUSY: $BUSY_ADVISORS"
    exit 0
else
    echo "❌ WORKER CRASH TEST FAILED"
    echo "  - Recovery detectado: ${DETECTED} en ${DETECTION_TIME:-timeout}s"
    exit 1
fi