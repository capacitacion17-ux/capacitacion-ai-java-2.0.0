#!/bin/bash
# Idempotency Test: Valida que operaciones duplicadas no causen inconsistencias
# Usage: ./scripts/concurrency/idempotency-test.sh

set -e

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║   TICKETERO - IDEMPOTENCY TEST (CONC-02)                     ║"
echo "╚══════════════════════════════════════════════════════════════╝"

# Cleanup
docker exec ticketero-postgres psql -U dev -d ticketero -c "
    DELETE FROM ticket_event; DELETE FROM outbox_message; DELETE FROM ticket;
    UPDATE advisor SET status = 'AVAILABLE';
" > /dev/null 2>&1

# Test 1: Mismo nationalId múltiples veces
echo "Test 1: Creando tickets con mismo nationalId..."
NATIONAL_ID="12345678"

for i in $(seq 1 10); do
    (
        curl -s -X POST "http://localhost:8080/api/tickets" \
            -H "Content-Type: application/json" \
            -d "{
                \"nationalId\": \"${NATIONAL_ID}\",
                \"telefono\": \"+56912345678\",
                \"branchOffice\": \"Sucursal Test\",
                \"queueType\": \"CAJA\"
            }" > /dev/null
    ) &
done
wait

sleep 10

# Validar que solo se creó 1 ticket por nationalId
TICKETS_SAME_ID=$(docker exec ticketero-postgres psql -U dev -d ticketero -t -c \
    "SELECT COUNT(*) FROM ticket WHERE national_id='${NATIONAL_ID}';" | xargs)

# Test 2: Operaciones de estado simultáneas
echo "Test 2: Operaciones de estado simultáneas..."
TICKET_ID=$(docker exec ticketero-postgres psql -U dev -d ticketero -t -c \
    "SELECT id FROM ticket LIMIT 1;" | xargs)

if [ -n "$TICKET_ID" ]; then
    # Simular múltiples llamadas simultáneas al mismo ticket
    for i in $(seq 1 5); do
        (
            curl -s -X PUT "http://localhost:8080/api/tickets/${TICKET_ID}/call" > /dev/null
        ) &
    done
    wait
    
    sleep 5
    
    # Validar que el ticket solo fue llamado una vez
    CALL_EVENTS=$(docker exec ticketero-postgres psql -U dev -d ticketero -t -c \
        "SELECT COUNT(*) FROM ticket_event WHERE ticket_id=${TICKET_ID} AND event_type='CALLED';" | xargs)
else
    CALL_EVENTS=0
fi

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "  RESULTADOS IDEMPOTENCY TEST"
echo "═══════════════════════════════════════════════════════════════"
echo "  Tickets mismo nationalId: ${TICKETS_SAME_ID} (esperado: 1)"
echo "  Eventos CALLED duplicados: ${CALL_EVENTS} (esperado: ≤1)"

if [ "$TICKETS_SAME_ID" -eq 1 ] && [ "$CALL_EVENTS" -le 1 ]; then
    echo "✅ IDEMPOTENCY TEST PASSED"
    echo "  Sistema previene operaciones duplicadas correctamente"
    exit 0
else
    echo "❌ IDEMPOTENCY TEST FAILED"
    echo "  Sistema permite operaciones duplicadas"
    exit 1
fi