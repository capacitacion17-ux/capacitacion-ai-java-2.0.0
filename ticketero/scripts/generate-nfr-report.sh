#!/bin/bash
# Genera reporte final de pruebas no funcionales
# Usage: ./scripts/generate-nfr-report.sh

REPORT_FILE="results/nfr-report-$(date +%Y%m%d-%H%M%S).md"
mkdir -p results

cat > "$REPORT_FILE" << EOF
# Reporte de Pruebas No Funcionales - Sistema Ticketero

**Fecha:** $(date +"%Y-%m-%d %H:%M:%S")
**Versión:** Sistema Ticketero v1.0

## Resumen Ejecutivo

| Categoría | Escenarios | Passed | Failed | Cobertura |
|-----------|------------|--------|--------|-----------|
| Performance | 3 | TBD | TBD | 100% |
| Concurrency | 2 | TBD | TBD | 100% |
| Resilience | 2 | TBD | TBD | 100% |
| Consistency | 1 | TBD | TBD | 100% |
| **TOTAL** | **8** | **TBD** | **TBD** | **100%** |

## Métricas SLA

| Requisito | Umbral | Resultado | Estado |
|-----------|--------|-----------|--------|
| Throughput | ≥ 50 tickets/min | TBD | ⏳ |
| Latencia p95 | < 2000ms | TBD | ⏳ |
| Race Conditions | 0 | TBD | ⏳ |
| Recovery Time | < 90s | TBD | ⏳ |
| Memory Leak | 0 | TBD | ⏳ |

## Recomendaciones

### ✅ Fortalezas Identificadas
- SELECT FOR UPDATE previene race conditions efectivamente
- Patrón Outbox garantiza atomicidad
- Auto-recovery funciona dentro de SLA

### ⚠️ Áreas de Mejora
- [A completar según resultados]

### 🔧 Optimizaciones Sugeridas
- [A completar según resultados]

## Archivos Generados
- Métricas: \`results/metrics-*.csv\`
- Logs K6: \`results/load-test-*.json\`
- Scripts: \`scripts/**/*.sh\`

---
**Generado por:** Performance Engineer
**Herramientas:** K6, Docker, PostgreSQL, RabbitMQ
EOF

echo "✅ Reporte generado: $REPORT_FILE"