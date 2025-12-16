#!/bin/bash
# =============================================================================
# TICKETERO - Monitor Dashboard
# =============================================================================
# Real-time monitoring of the ticket queue system
# Usage: ./scripts/monitor.sh [refresh_seconds]
# =============================================================================

REFRESH=${1:-3}  # Default refresh every 3 seconds

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color
BOLD='\033[1m'

# Check if containers are running
check_containers() {
    if ! docker ps | grep -q ticketero-app; then
        echo -e "${RED}ERROR: Containers not running. Start with: docker compose up -d${NC}"
        exit 1
    fi
}

# Get ticket stats
get_ticket_stats() {
    docker exec ticketero-postgres psql -U dev -d ticketero -t -c \
        "SELECT status, COUNT(*) FROM ticket GROUP BY status ORDER BY status;" 2>/dev/null
}

# Get advisor stats
get_advisor_stats() {
    docker exec ticketero-postgres psql -U dev -d ticketero -t -c \
        "SELECT name, status, total_tickets_served FROM advisor ORDER BY name;" 2>/dev/null
}

# Get queue stats
get_queue_stats() {
    docker exec ticketero-rabbitmq rabbitmqctl list_queues name messages 2>/dev/null | grep -v "^Timeout" | grep -v "^Listing"
}

# Get outbox stats
get_outbox_stats() {
    docker exec ticketero-postgres psql -U dev -d ticketero -t -c \
        "SELECT status, COUNT(*) FROM outbox_message GROUP BY status;" 2>/dev/null
}

# Get recent activity
get_recent_activity() {
    docker exec ticketero-postgres psql -U dev -d ticketero -t -c \
        "SELECT numero, queue_type, status,
                CASE WHEN completed_at IS NOT NULL
                     THEN EXTRACT(EPOCH FROM (completed_at - created_at))::int || 's'
                     ELSE '-' END as duration
         FROM ticket ORDER BY created_at DESC LIMIT 8;" 2>/dev/null
}

# Main dashboard
show_dashboard() {
    clear

    echo -e "${BOLD}${CYAN}╔══════════════════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BOLD}${CYAN}║                        TICKETERO - MONITOR DASHBOARD                        ║${NC}"
    echo -e "${BOLD}${CYAN}╚══════════════════════════════════════════════════════════════════════════════╝${NC}"
    echo -e "${BLUE}  $(date '+%Y-%m-%d %H:%M:%S')                                    Refresh: ${REFRESH}s (Ctrl+C to exit)${NC}"
    echo ""

    # -------------------------------------------------------------------------
    # TICKETS STATUS
    # -------------------------------------------------------------------------
    echo -e "${BOLD}${YELLOW}┌─────────────────────────────────────────────────────────────────────────────┐${NC}"
    echo -e "${BOLD}${YELLOW}│ 🎫 TICKETS                                                                  │${NC}"
    echo -e "${BOLD}${YELLOW}└─────────────────────────────────────────────────────────────────────────────┘${NC}"

    WAITING=0
    CALLED=0
    IN_PROGRESS=0
    COMPLETED=0

    while IFS='|' read -r status count; do
        status=$(echo "$status" | xargs)
        count=$(echo "$count" | xargs)
        case "$status" in
            WAITING) WAITING=$count ;;
            CALLED) CALLED=$count ;;
            IN_PROGRESS) IN_PROGRESS=$count ;;
            COMPLETED) COMPLETED=$count ;;
        esac
    done <<< "$(get_ticket_stats)"

    TOTAL=$((WAITING + CALLED + IN_PROGRESS + COMPLETED))

    echo -e "  ${CYAN}WAITING${NC}      ${YELLOW}CALLED${NC}       ${BLUE}IN_PROGRESS${NC}  ${GREEN}COMPLETED${NC}    ${BOLD}TOTAL${NC}"
    echo -e "  ${CYAN}${WAITING}${NC}            ${YELLOW}${CALLED}${NC}            ${BLUE}${IN_PROGRESS}${NC}            ${GREEN}${COMPLETED}${NC}            ${BOLD}${TOTAL}${NC}"
    echo ""

    # -------------------------------------------------------------------------
    # ADVISORS STATUS
    # -------------------------------------------------------------------------
    echo -e "${BOLD}${YELLOW}┌─────────────────────────────────────────────────────────────────────────────┐${NC}"
    echo -e "${BOLD}${YELLOW}│ 👥 ASESORES                                                                 │${NC}"
    echo -e "${BOLD}${YELLOW}└─────────────────────────────────────────────────────────────────────────────┘${NC}"

    printf "  ${BOLD}%-20s %-15s %-10s${NC}\n" "NOMBRE" "ESTADO" "ATENDIDOS"
    echo "  ────────────────────────────────────────────────"

    while IFS='|' read -r name status served; do
        name=$(echo "$name" | xargs)
        status=$(echo "$status" | xargs)
        served=$(echo "$served" | xargs)

        if [ -n "$name" ]; then
            if [ "$status" = "AVAILABLE" ]; then
                status_color="${GREEN}"
            else
                status_color="${RED}"
            fi
            printf "  %-20s ${status_color}%-15s${NC} %-10s\n" "$name" "$status" "$served"
        fi
    done <<< "$(get_advisor_stats)"
    echo ""

    # -------------------------------------------------------------------------
    # RABBITMQ QUEUES
    # -------------------------------------------------------------------------
    echo -e "${BOLD}${YELLOW}┌─────────────────────────────────────────────────────────────────────────────┐${NC}"
    echo -e "${BOLD}${YELLOW}│ 🐰 COLAS RABBITMQ                                                           │${NC}"
    echo -e "${BOLD}${YELLOW}└─────────────────────────────────────────────────────────────────────────────┘${NC}"

    printf "  ${BOLD}%-25s %-10s${NC}\n" "COLA" "MENSAJES"
    echo "  ────────────────────────────────────"

    while read -r queue messages; do
        if [ -n "$queue" ] && [[ "$queue" == *-queue ]]; then
            if [ "$messages" -gt 0 ] 2>/dev/null; then
                msg_color="${YELLOW}"
            else
                msg_color="${GREEN}"
            fi
            printf "  %-25s ${msg_color}%-10s${NC}\n" "$queue" "$messages"
        fi
    done <<< "$(get_queue_stats)"
    echo ""

    # -------------------------------------------------------------------------
    # OUTBOX STATUS
    # -------------------------------------------------------------------------
    echo -e "${BOLD}${YELLOW}┌─────────────────────────────────────────────────────────────────────────────┐${NC}"
    echo -e "${BOLD}${YELLOW}│ 📤 OUTBOX (Patrón Transaccional)                                            │${NC}"
    echo -e "${BOLD}${YELLOW}└─────────────────────────────────────────────────────────────────────────────┘${NC}"

    PENDING=0
    SENT=0
    FAILED=0

    while IFS='|' read -r status count; do
        status=$(echo "$status" | xargs)
        count=$(echo "$count" | xargs)
        case "$status" in
            PENDING) PENDING=$count ;;
            SENT) SENT=$count ;;
            FAILED) FAILED=$count ;;
        esac
    done <<< "$(get_outbox_stats)"

    echo -e "  ${YELLOW}PENDING${NC}      ${GREEN}SENT${NC}         ${RED}FAILED${NC}"
    echo -e "  ${YELLOW}${PENDING}${NC}            ${GREEN}${SENT}${NC}            ${RED}${FAILED}${NC}"
    echo ""

    # -------------------------------------------------------------------------
    # RECENT ACTIVITY
    # -------------------------------------------------------------------------
    echo -e "${BOLD}${YELLOW}┌─────────────────────────────────────────────────────────────────────────────┐${NC}"
    echo -e "${BOLD}${YELLOW}│ 📋 ACTIVIDAD RECIENTE                                                       │${NC}"
    echo -e "${BOLD}${YELLOW}└─────────────────────────────────────────────────────────────────────────────┘${NC}"

    printf "  ${BOLD}%-8s %-12s %-15s %-10s${NC}\n" "TICKET" "COLA" "ESTADO" "DURACIÓN"
    echo "  ─────────────────────────────────────────────────"

    while IFS='|' read -r numero queue status duration; do
        numero=$(echo "$numero" | xargs)
        queue=$(echo "$queue" | xargs)
        status=$(echo "$status" | xargs)
        duration=$(echo "$duration" | xargs)

        if [ -n "$numero" ]; then
            case "$status" in
                WAITING) status_color="${CYAN}" ;;
                CALLED) status_color="${YELLOW}" ;;
                IN_PROGRESS) status_color="${BLUE}" ;;
                COMPLETED) status_color="${GREEN}" ;;
                *) status_color="${NC}" ;;
            esac
            printf "  %-8s %-12s ${status_color}%-15s${NC} %-10s\n" "$numero" "$queue" "$status" "$duration"
        fi
    done <<< "$(get_recent_activity)"

    echo ""
    echo -e "${CYAN}──────────────────────────────────────────────────────────────────────────────${NC}"
}

# Main loop
check_containers

while true; do
    show_dashboard
    sleep "$REFRESH"
done
