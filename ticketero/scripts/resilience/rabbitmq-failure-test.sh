#!/bin/bash
# RabbitMQ Failure Test: Valida comportamiento ante fallas de mensajería
# Usage: ./scripts/resilience/rabbitmq-failure-test.sh

set -e

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║   TICKETERO - RABBITMQ FAILURE TEST (RES-02)                 ║"
echo "╚══════════════════════════════════════════════════════════════╝"

# Cleanup
docker exec ticketero-postgres psql -U dev -d ticketero -c "
    DELETE FROM ticket_event; DELETE FROM outbox_message; DELETE FROM ticket;
    UPDATE advisor SET status = 'AVAILABLE';
" > /dev/null 2>&1

# Crear algunos tickets antes de la falla
echo "1. Creando tickets antes de falla RabbitMQ..."
for i in $(seq 1 5); do
    curl -s -X POST "http://localhost:8080/api/tickets" \
        -H "Content-Type: application/json" \
        -d "{
            \"nationalId\": \"700000$(printf '%03d' $i)\",
            \"telefono\": \"+56912345678\",
            \"branchOffice\": \"Sucursal Test\",
            \"queueType\": \"CAJA\"
        }" > /dev/null
done

sleep 10

# Simular falla de RabbitMQ
echo "2. Simulando falla de RabbitMQ..."
docker stop ticketero-rabbitmq > /dev/null 2>&1

# Crear tickets durante la falla
echo "3. Creando tickets durante falla..."
for i in $(seq 6 10); do
    curl -s -X POST "http://localhost:8080/api/tickets" \
        -H "Content-Type: application/json" \
        -d "{
            \"nationalId\": \"700000$(printf '%03d' $i)\",
            \"telefono\": \"+56912345678\",
            \"branchOffice\": \"Sucursal Test\",
            \"queueType\": \"CAJA\"
        }" > /dev/null
done

sleep 5

# Verificar que los tickets se guardaron en outbox
OUTBOX_PENDING=$(docker exec ticketero-postgres psql -U dev -d ticketero -t -c \
    "SELECT COUNT(*) FROM outbox_message WHERE status='PENDING';" | xargs)

# Restaurar RabbitMQ
echo "4. Restaurando RabbitMQ..."
docker start ticketero-rabbitmq > /dev/null 2>&1
sleep 15  # Esperar que RabbitMQ esté listo

# Esperar procesamiento de mensajes pendientes
echo "5. Esperando procesamiento de mensajes pendientes..."
sleep 30

# Verificar que los mensajes se procesaron
OUTBOX_PROCESSED=$(docker exec ticketero-postgres psql -U dev -d ticketero -t -c \
    "SELECT COUNT(*) FROM outbox_message WHERE status='SENT';" | xargs)

TOTAL_TICKETS=$(docker exec ticketero-postgres psql -U dev -d ticketero -t -c \
    "SELECT COUNT(*) FROM ticket;" | xargs)

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "  RESULTADOS RABBITMQ FAILURE TEST"
echo "═══════════════════════════════════════════════════════════════"
echo "  Total tickets creados:     ${TOTAL_TICKETS}"
echo "  Mensajes pendientes:       ${OUTBOX_PENDING}"
echo "  Mensajes procesados:       ${OUTBOX_PROCESSED}"

if [ "$TOTAL_TICKETS" -eq 10 ] && [ "$OUTBOX_PROCESSED" -ge 8 ]; then
    echo "✅ RABBITMQ FAILURE TEST PASSED"
    echo "  Sistema mantiene consistencia durante fallas de mensajería"
    exit 0
else
    echo "❌ RABBITMQ FAILURE TEST FAILED"
    echo "  Sistema no maneja correctamente fallas de mensajería"
    exit 1
fi