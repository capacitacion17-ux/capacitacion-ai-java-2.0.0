@echo off
REM Hace ejecutables todos los scripts de pruebas no funcionales
REM Usage: scripts\make-executable.bat

echo Configurando permisos de ejecucion para scripts NFR...

REM Performance tests
chmod +x scripts\performance\load-test.sh
chmod +x scripts\performance\spike-test.sh  
chmod +x scripts\performance\soak-test.sh

REM Concurrency tests
chmod +x scripts\concurrency\race-condition-test.sh
chmod +x scripts\concurrency\idempotency-test.sh

REM Resilience tests
chmod +x scripts\resilience\worker-crash-test.sh
chmod +x scripts\resilience\rabbitmq-failure-test.sh

REM Consistency tests
chmod +x scripts\consistency\outbox-atomicity-test.sh

REM Utils
chmod +x scripts\utils\metrics-collector.sh
chmod +x scripts\utils\validate-consistency.sh

REM Main scripts
chmod +x scripts\run-all-nfr-tests.sh
chmod +x scripts\generate-nfr-report.sh

echo ✅ Permisos configurados correctamente
echo.
echo Para ejecutar la suite completa:
echo   bash scripts/run-all-nfr-tests.sh
echo.
echo Para ejecutar tests individuales:
echo   bash scripts/performance/load-test.sh
echo   bash scripts/concurrency/race-condition-test.sh
echo   etc.