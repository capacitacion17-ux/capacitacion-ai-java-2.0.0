#!/bin/bash
# Recolecta métricas del sistema durante pruebas
# Usage: ./scripts/utils/metrics-collector.sh [duration] [output_file]

DURATION=${1:-60}
OUTPUT_FILE=${2:-"metrics-$(date +%Y%m%d-%H%M%S).csv"}

echo "timestamp,cpu_app,mem_app_mb,db_connections,tickets_waiting,tickets_completed,outbox_pending" > "$OUTPUT_FILE"

START_TIME=$(date +%s)
END_TIME=$((START_TIME + DURATION))

while [ $(date +%s) -lt $END_TIME ]; do
    TIMESTAMP=$(date +%Y-%m-%d\ %H:%M:%S)
    
    # Container stats
    APP_STATS=$(docker stats ticketero-app --no-stream --format "{{.CPUPerc}},{{.MemUsage}}" 2>/dev/null | head -1)
    APP_CPU=$(echo "$APP_STATS" | cut -d',' -f1 | tr -d '%')
    APP_MEM=$(echo "$APP_STATS" | cut -d',' -f2 | cut -d'/' -f1 | tr -d 'MiB ')
    
    # Database metrics
    DB_CONNECTIONS=$(docker exec ticketero-postgres psql -U dev -d ticketero -t -c \
        "SELECT count(*) FROM pg_stat_activity WHERE datname='ticketero';" 2>/dev/null | xargs)
    
    # Ticket stats
    TICKETS_WAITING=$(docker exec ticketero-postgres psql -U dev -d ticketero -t -c \
        "SELECT COUNT(*) FROM ticket WHERE status='WAITING';" 2>/dev/null | xargs)
    TICKETS_COMPLETED=$(docker exec ticketero-postgres psql -U dev -d ticketero -t -c \
        "SELECT COUNT(*) FROM ticket WHERE status='COMPLETED';" 2>/dev/null | xargs)
    
    # Outbox stats
    OUTBOX_PENDING=$(docker exec ticketero-postgres psql -U dev -d ticketero -t -c \
        "SELECT COUNT(*) FROM outbox_message WHERE status='PENDING';" 2>/dev/null | xargs)
    
    echo "${TIMESTAMP},${APP_CPU:-0},${APP_MEM:-0},${DB_CONNECTIONS:-0},${TICKETS_WAITING:-0},${TICKETS_COMPLETED:-0},${OUTBOX_PENDING:-0}" >> "$OUTPUT_FILE"
    
    sleep 5
done

echo "✅ Metrics collection complete: ${OUTPUT_FILE}"