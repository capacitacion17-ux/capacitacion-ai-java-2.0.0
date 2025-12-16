#!/bin/bash
# Outbox Atomicity Test: Valida transaccionalidad Ticket + Outbox
# Usage: ./scripts/consistency/outbox-atomicity-test.sh

set -e

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║   TICKETERO - OUTBOX ATOMICITY TEST (CONS-01)                ║"
echo "╚══════════════════════════════════════════════════════════════╝"

# Cleanup
docker exec ticketero-postgres psql -U dev -d ticketero -c "
    DELETE FROM outbox_message; DELETE FROM ticket;
" > /dev/null 2>&1

# Crear 50 tickets simultáneos
echo "Creando 50 tickets simultáneamente..."
for i in $(seq 1 50); do
    (
        curl -s -X POST "http://localhost:8080/api/tickets" \
            -H "Content-Type: application/json" \
            -d "{
                \"nationalId\": \"800$(printf '%05d' $i)\",
                \"telefono\": \"+56912345678\",
                \"branchOffice\": \"Sucursal Test\",
                \"queueType\": \"CAJA\"
            }" > /dev/null
    ) &
done
wait

# Validar atomicidad: cada ticket debe tener exactamente 1 mensaje outbox
TICKETS=$(docker exec ticketero-postgres psql -U dev -d ticketero -t -c \
    "SELECT COUNT(*) FROM ticket;" | xargs)
OUTBOX_MESSAGES=$(docker exec ticketero-postgres psql -U dev -d ticketero -t -c \
    "SELECT COUNT(*) FROM outbox_message;" | xargs)

# Validar que no hay tickets huérfanos
ORPHAN_TICKETS=$(docker exec ticketero-postgres psql -U dev -d ticketero -t -c "
    SELECT COUNT(*) FROM ticket t
    WHERE NOT EXISTS (
        SELECT 1 FROM outbox_message o 
        WHERE o.aggregate_id = t.id::text
    );
" | xargs)

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "  RESULTADOS OUTBOX ATOMICITY TEST"
echo "═══════════════════════════════════════════════════════════════"
echo "  Tickets creados:     $TICKETS"
echo "  Mensajes Outbox:     $OUTBOX_MESSAGES"
echo "  Tickets huérfanos:   $ORPHAN_TICKETS"

if [ "$TICKETS" -eq "$OUTBOX_MESSAGES" ] && [ "$ORPHAN_TICKETS" -eq 0 ]; then
    echo "✅ OUTBOX ATOMICITY TEST PASSED"
    echo "  Atomicidad Ticket + Outbox garantizada"
    exit 0
else
    echo "❌ OUTBOX ATOMICITY TEST FAILED"
    echo "  Atomicidad comprometida"
    exit 1
fi