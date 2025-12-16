#!/bin/bash
# Valida que el entorno esté listo para pruebas NFR
# Usage: ./scripts/validate-setup.sh

set -e

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║           TICKETERO - VALIDACIÓN DE SETUP NFR               ║"
echo "╚══════════════════════════════════════════════════════════════╝"

ERRORS=0

# 1. Docker disponible
echo -n "1. Docker disponible... "
if command -v docker &> /dev/null && docker ps &> /dev/null; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗${NC}"
    ERRORS=$((ERRORS + 1))
fi

# 2. Servicios Ticketero corriendo
echo -n "2. Servicios Ticketero... "
if docker ps | grep -q ticketero-app && docker ps | grep -q ticketero-postgres && docker ps | grep -q ticketero-rabbitmq; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗${NC} (ejecutar: docker compose up -d)"
    ERRORS=$((ERRORS + 1))
fi

# 3. API respondiendo
echo -n "3. API Ticketero... "
if curl -s -f http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗${NC} (API no responde en puerto 8080)"
    ERRORS=$((ERRORS + 1))
fi

# 4. Base de datos accesible
echo -n "4. PostgreSQL... "
if docker exec ticketero-postgres psql -U dev -d ticketero -c "SELECT 1;" > /dev/null 2>&1; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗${NC} (PostgreSQL no accesible)"
    ERRORS=$((ERRORS + 1))
fi

# 5. RabbitMQ funcionando
echo -n "5. RabbitMQ... "
if docker exec ticketero-rabbitmq rabbitmqctl status > /dev/null 2>&1; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗${NC} (RabbitMQ no funciona)"
    ERRORS=$((ERRORS + 1))
fi

# 6. Scripts ejecutables
echo -n "6. Scripts ejecutables... "
if [ -x "scripts/run-all-nfr-tests.sh" ]; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${YELLOW}⚠${NC} (ejecutar: scripts/make-executable.bat)"
fi

# 7. Directorio results
echo -n "7. Directorio results... "
mkdir -p results
echo -e "${GREEN}✓${NC}"

# 8. Herramientas opcionales
echo -n "8. K6 disponible... "
if command -v k6 &> /dev/null; then
    echo -e "${GREEN}✓${NC} (performance mejorada)"
else
    echo -e "${YELLOW}⚠${NC} (fallback a curl)"
fi

echo ""
echo "═══════════════════════════════════════════════════════════════"

if [ $ERRORS -eq 0 ]; then
    echo -e "${GREEN}✅ SETUP COMPLETO - LISTO PARA PRUEBAS NFR${NC}"
    echo ""
    echo "Para ejecutar:"
    echo "  bash scripts/run-all-nfr-tests.sh"
    exit 0
else
    echo -e "${RED}❌ $ERRORS ERRORES DE SETUP${NC}"
    echo ""
    echo "Soluciones:"
    echo "  1. Instalar Docker Desktop"
    echo "  2. Ejecutar: docker compose up -d"
    echo "  3. Esperar que servicios estén listos"
    echo "  4. Ejecutar: scripts/make-executable.bat"
    exit 1
fi