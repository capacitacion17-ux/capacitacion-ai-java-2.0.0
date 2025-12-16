#!/bin/bash
# Spike Test: 50 tickets simultáneos en 10 segundos
# Usage: ./scripts/performance/spike-test.sh

set -e

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║        TICKETERO - SPIKE TEST (PERF-02)                      ║"
echo "╚══════════════════════════════════════════════════════════════╝"

# Cleanup
docker exec ticketero-postgres psql -U dev -d ticketero -c "
    DELETE FROM ticket_event; DELETE FROM outbox_message; DELETE FROM ticket;
    UPDATE advisor SET status = 'AVAILABLE';
" > /dev/null 2>&1

# Execute spike
echo "Ejecutando spike (50 tickets en paralelo)..."
START_TIME=$(date +%s)

for i in $(seq 1 50); do
    (
        curl -s -X POST "http://localhost:8080/api/tickets" \
            -H "Content-Type: application/json" \
            -d "{
                \"nationalId\": \"400000$(printf '%03d' $i)\",
                \"telefono\": \"+5691234${i}\",
                \"branchOffice\": \"Sucursal Test\",
                \"queueType\": \"CAJA\"
            }" > /dev/null
    ) &
done

wait
SPIKE_END=$(date +%s)
SPIKE_DURATION=$((SPIKE_END - START_TIME))

echo "✓ Spike completado en ${SPIKE_DURATION} segundos"

# Wait for processing
echo "Esperando procesamiento..."
sleep 60

# Validate
COMPLETED=$(docker exec ticketero-postgres psql -U dev -d ticketero -t -c \
    "SELECT COUNT(*) FROM ticket WHERE status='COMPLETED';" | xargs)

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "  RESULTADOS SPIKE TEST"
echo "═══════════════════════════════════════════════════════════════"
echo "  Tickets creados:     50 en ${SPIKE_DURATION}s"
echo "  Tickets completados: ${COMPLETED}"

if [ "$COMPLETED" -ge 45 ]; then
    echo "✅ SPIKE TEST PASSED (90%+ completados)"
    exit 0
else
    echo "❌ SPIKE TEST FAILED (<90% completados)"
    exit 1
fi