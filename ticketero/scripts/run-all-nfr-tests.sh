#!/bin/bash
# Ejecuta toda la suite de pruebas no funcionales
# Usage: ./scripts/run-all-nfr-tests.sh

set -e

CYAN='\033[0;36m'
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${CYAN}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║           TICKETERO - SUITE PRUEBAS NO FUNCIONALES           ║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════════════════════════╝${NC}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESULTS_DIR="$SCRIPT_DIR/../results"
mkdir -p "$RESULTS_DIR"

# Contadores
TOTAL_TESTS=8
PASSED_TESTS=0
FAILED_TESTS=0

# Array para almacenar resultados
declare -a TEST_RESULTS

# Función para ejecutar test
run_test() {
    local test_name="$1"
    local test_script="$2"
    local category="$3"
    
    echo -e "${YELLOW}Ejecutando: $test_name${NC}"
    
    if bash "$test_script"; then
        echo -e "${GREEN}✅ $test_name PASSED${NC}"
        TEST_RESULTS+=("$category:$test_name:PASSED")
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        echo -e "${RED}❌ $test_name FAILED${NC}"
        TEST_RESULTS+=("$category:$test_name:FAILED")
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi
    
    echo ""
    sleep 5  # Pausa entre tests
}

# Verificar que Docker esté corriendo
if ! docker ps > /dev/null 2>&1; then
    echo -e "${RED}❌ Docker no está corriendo. Iniciando servicios...${NC}"
    exit 1
fi

# Verificar servicios
echo -e "${YELLOW}Verificando servicios...${NC}"
if ! docker ps | grep -q ticketero-app; then
    echo -e "${RED}❌ Servicios Ticketero no están corriendo. Ejecutar: docker compose up -d${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Servicios verificados${NC}"
echo ""

# PASO 1: Performance Tests
echo -e "${CYAN}═══ PASO 1: PERFORMANCE TESTS ═══${NC}"
run_test "Load Test Sostenido" "$SCRIPT_DIR/performance/load-test.sh" "Performance"
run_test "Spike Test" "$SCRIPT_DIR/performance/spike-test.sh" "Performance"
run_test "Soak Test" "$SCRIPT_DIR/performance/soak-test.sh" "Performance"

# PASO 2: Concurrency Tests
echo -e "${CYAN}═══ PASO 2: CONCURRENCY TESTS ═══${NC}"
run_test "Race Condition Test" "$SCRIPT_DIR/concurrency/race-condition-test.sh" "Concurrency"
run_test "Idempotency Test" "$SCRIPT_DIR/concurrency/idempotency-test.sh" "Concurrency"

# PASO 3: Resilience Tests
echo -e "${CYAN}═══ PASO 3: RESILIENCE TESTS ═══${NC}"
run_test "Worker Crash Test" "$SCRIPT_DIR/resilience/worker-crash-test.sh" "Resilience"
run_test "RabbitMQ Failure Test" "$SCRIPT_DIR/resilience/rabbitmq-failure-test.sh" "Resilience"

# PASO 4: Consistency Tests
echo -e "${CYAN}═══ PASO 4: CONSISTENCY TESTS ═══${NC}"
run_test "Outbox Atomicity Test" "$SCRIPT_DIR/consistency/outbox-atomicity-test.sh" "Consistency"

# Validación final de consistencia
echo -e "${CYAN}═══ VALIDACIÓN FINAL ═══${NC}"
"$SCRIPT_DIR/utils/validate-consistency.sh"
FINAL_CONSISTENCY=$?

# Generar reporte
echo -e "${YELLOW}Generando reporte final...${NC}"
bash "$SCRIPT_DIR/generate-nfr-report.sh"

# Resumen final
echo ""
echo -e "${CYAN}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║                    RESUMEN FINAL                             ║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo "  Total Tests:        $TOTAL_TESTS"
echo -e "  Tests Passed:       ${GREEN}$PASSED_TESTS${NC}"
echo -e "  Tests Failed:       ${RED}$FAILED_TESTS${NC}"
echo ""

# Desglose por categoría
echo "Resultados por categoría:"
for result in "${TEST_RESULTS[@]}"; do
    IFS=':' read -r category name status <<< "$result"
    if [ "$status" = "PASSED" ]; then
        echo -e "  $category - $name: ${GREEN}$status${NC}"
    else
        echo -e "  $category - $name: ${RED}$status${NC}"
    fi
done

echo ""
if [ $FINAL_CONSISTENCY -eq 0 ]; then
    echo -e "  Consistencia Final: ${GREEN}PASS${NC}"
else
    echo -e "  Consistencia Final: ${RED}FAIL${NC}"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"

# Exit code
if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "${GREEN}🎉 TODOS LOS TESTS PASARON - SISTEMA LISTO PARA PRODUCCIÓN${NC}"
    exit 0
else
    echo -e "${RED}⚠️  $FAILED_TESTS TESTS FALLARON - REVISAR ANTES DE PRODUCCIÓN${NC}"
    exit 1
fi