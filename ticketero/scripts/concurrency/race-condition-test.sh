#!/bin/bash
# Race Condition Test: Valida SELECT FOR UPDATE
# Usage: ./scripts/concurrency/race-condition-test.sh

set -e

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║   TICKETERO - RACE CONDITION TEST (CONC-01)                  ║"
echo "╚══════════════════════════════════════════════════════════════╝"

# Setup: Solo 1 asesor disponible
docker exec ticketero-postgres psql -U dev -d ticketero -c "
    DELETE FROM ticket_event; DELETE FROM outbox_message; DELETE FROM ticket;
    UPDATE advisor SET status = 'BREAK';
    UPDATE advisor SET status = 'AVAILABLE' WHERE id = 1;
" > /dev/null 2>&1

# Crear 5 tickets simultáneos para cola CAJA
echo "Creando 5 tickets simultáneamente..."
for i in $(seq 1 5); do
    (
        curl -s -X POST "http://localhost:8080/api/tickets" \
            -H "Content-Type: application/json" \
            -d "{
                \"nationalId\": \"600000$(printf '%03d' $i)\",
                \"telefono\": \"+5691234${i}\",
                \"branchOffice\": \"Sucursal Test\",
                \"queueType\": \"CAJA\"
            }" > /dev/null
    ) &
done
wait

# Esperar procesamiento
sleep 30

# Validar que no hay asignaciones dobles
DOUBLE_ASSIGNED=$(docker exec ticketero-postgres psql -U dev -d ticketero -t -c "
    SELECT COUNT(*) FROM (
        SELECT assigned_advisor_id, COUNT(*) 
        FROM ticket 
        WHERE assigned_advisor_id IS NOT NULL 
        AND status IN ('CALLED', 'IN_PROGRESS')
        GROUP BY assigned_advisor_id 
        HAVING COUNT(*) > 1
    ) doubles;
" | xargs)

# Verificar deadlocks
DEADLOCKS=$(docker exec ticketero-postgres psql -U dev -d ticketero -t -c \
    "SELECT deadlocks FROM pg_stat_database WHERE datname='ticketero';" | xargs)

# Cleanup
docker exec ticketero-postgres psql -U dev -d ticketero -c \
    "UPDATE advisor SET status = 'AVAILABLE';" > /dev/null 2>&1

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "  RESULTADOS RACE CONDITION TEST"
echo "═══════════════════════════════════════════════════════════════"

if [ "$DOUBLE_ASSIGNED" -eq 0 ] && [ "${DEADLOCKS:-0}" -eq 0 ]; then
    echo "✅ RACE CONDITION TEST PASSED"
    echo "  - Asignaciones dobles: 0"
    echo "  - Deadlocks: 0"
    echo "  SELECT FOR UPDATE funcionando correctamente"
    exit 0
else
    echo "❌ RACE CONDITION TEST FAILED"
    echo "  - Asignaciones dobles: $DOUBLE_ASSIGNED"
    echo "  - Deadlocks: ${DEADLOCKS:-0}"
    exit 1
fi