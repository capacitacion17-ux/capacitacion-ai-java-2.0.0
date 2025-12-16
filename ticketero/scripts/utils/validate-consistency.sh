#!/bin/bash
# Valida consistencia del sistema post-pruebas
# Usage: ./scripts/utils/validate-consistency.sh

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "═══════════════════════════════════════════════════════════════"
echo "  TICKETERO - VALIDACIÓN DE CONSISTENCIA"
echo "═══════════════════════════════════════════════════════════════"

ERRORS=0

# 1. Tickets en estado inconsistente
echo -n "1. Tickets inconsistentes... "
INCONSISTENT=$(docker exec ticketero-postgres psql -U dev -d ticketero -t -c "
    SELECT COUNT(*) FROM ticket t
    WHERE (t.status = 'IN_PROGRESS' AND t.started_at IS NULL)
       OR (t.status = 'COMPLETED' AND t.completed_at IS NULL)
       OR (t.status = 'CALLED' AND t.assigned_advisor_id IS NULL);
" | xargs)

if [ "$INCONSISTENT" -eq 0 ]; then
    echo -e "${GREEN}PASS${NC} (0 encontrados)"
else
    echo -e "${RED}FAIL${NC} ($INCONSISTENT encontrados)"
    ERRORS=$((ERRORS + 1))
fi

# 2. Asesores BUSY sin ticket activo
echo -n "2. Asesores BUSY sin ticket... "
BUSY_NO_TICKET=$(docker exec ticketero-postgres psql -U dev -d ticketero -t -c "
    SELECT COUNT(*) FROM advisor a
    WHERE a.status = 'BUSY'
    AND NOT EXISTS (
        SELECT 1 FROM ticket t 
        WHERE t.assigned_advisor_id = a.id 
        AND t.status IN ('CALLED', 'IN_PROGRESS')
    );
" | xargs)

if [ "$BUSY_NO_TICKET" -eq 0 ]; then
    echo -e "${GREEN}PASS${NC}"
else
    echo -e "${YELLOW}WARN${NC} ($BUSY_NO_TICKET encontrados)"
fi

# 3. Mensajes Outbox fallidos
echo -n "3. Mensajes Outbox FAILED... "
OUTBOX_FAILED=$(docker exec ticketero-postgres psql -U dev -d ticketero -t -c \
    "SELECT COUNT(*) FROM outbox_message WHERE status='FAILED';" | xargs)

if [ "$OUTBOX_FAILED" -eq 0 ]; then
    echo -e "${GREEN}PASS${NC}"
else
    echo -e "${RED}FAIL${NC} ($OUTBOX_FAILED fallidos)"
    ERRORS=$((ERRORS + 1))
fi

# 4. Deadlocks PostgreSQL
echo -n "4. Deadlocks PostgreSQL... "
DEADLOCKS=$(docker exec ticketero-postgres psql -U dev -d ticketero -t -c \
    "SELECT deadlocks FROM pg_stat_database WHERE datname='ticketero';" | xargs)

if [ "${DEADLOCKS:-0}" -eq 0 ]; then
    echo -e "${GREEN}PASS${NC}"
else
    echo -e "${RED}FAIL${NC} ($DEADLOCKS deadlocks)"
    ERRORS=$((ERRORS + 1))
fi

echo ""
echo "═══════════════════════════════════════════════════════════════"
if [ $ERRORS -eq 0 ]; then
    echo -e "  RESULTADO: ${GREEN}SISTEMA CONSISTENTE${NC}"
else
    echo -e "  RESULTADO: ${RED}$ERRORS ERRORES DE CONSISTENCIA${NC}"
fi
echo "═══════════════════════════════════════════════════════════════"

exit $ERRORS