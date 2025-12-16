#!/bin/bash
# =============================================================================
# TICKETERO - Load Test Script
# =============================================================================
# Creates tickets for load testing
# Usage: ./scripts/load-test.sh [num_tickets] [delay_ms]
# =============================================================================

NUM_TICKETS=${1:-10}
DELAY_MS=${2:-500}

API_URL="http://localhost:8080/api/tickets"
QUEUES=("CAJA" "PERSONAL" "EMPRESAS" "CAJA")

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║              TICKETERO - PRUEBA DE CARGA                     ║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "  Tickets a crear: ${YELLOW}${NUM_TICKETS}${NC}"
echo -e "  Delay entre tickets: ${YELLOW}${DELAY_MS}ms${NC}"
echo ""
echo -e "${CYAN}────────────────────────────────────────────────────────────────${NC}"
echo ""

# Check if API is available
if ! curl -s http://localhost:8080/actuator/health | grep -q "UP"; then
    echo -e "${YELLOW}ERROR: API no disponible. Verifica que los contenedores estén corriendo.${NC}"
    exit 1
fi

CREATED=0
FAILED=0

for i in $(seq 1 $NUM_TICKETS); do
    QUEUE_INDEX=$((i % 4))
    QUEUE=${QUEUES[$QUEUE_INDEX]}
    NATIONAL_ID="200000$(printf '%03d' $i)"
    TELEFONO="56900000$(printf '%03d' $i)"

    RESPONSE=$(curl -s -X POST "$API_URL" \
        -H "Content-Type: application/json" \
        -d "{
            \"nationalId\": \"${NATIONAL_ID}\",
            \"telefono\": \"${TELEFONO}\",
            \"branchOffice\": \"Sucursal Test\",
            \"queueType\": \"${QUEUE}\"
        }")

    TICKET_NUM=$(echo "$RESPONSE" | grep -o '"numero":"[^"]*"' | cut -d'"' -f4)

    if [ -n "$TICKET_NUM" ]; then
        echo -e "  ${GREEN}✓${NC} Ticket ${YELLOW}#${i}${NC}: ${CYAN}${TICKET_NUM}${NC} → ${QUEUE}"
        CREATED=$((CREATED + 1))
    else
        echo -e "  ${YELLOW}✗${NC} Ticket #${i}: Error"
        FAILED=$((FAILED + 1))
    fi

    # Delay between requests (convert ms to seconds)
    if [ $i -lt $NUM_TICKETS ]; then
        sleep $(echo "scale=3; $DELAY_MS/1000" | bc)
    fi
done

echo ""
echo -e "${CYAN}────────────────────────────────────────────────────────────────${NC}"
echo ""
echo -e "  ${GREEN}Creados: ${CREATED}${NC}"
echo -e "  ${YELLOW}Fallidos: ${FAILED}${NC}"
echo ""
echo -e "${CYAN}Usa ./scripts/monitor.sh para ver el procesamiento en tiempo real${NC}"
