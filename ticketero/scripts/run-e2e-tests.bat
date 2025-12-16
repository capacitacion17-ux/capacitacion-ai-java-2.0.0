@echo off
REM Script para ejecutar todos los tests E2E del sistema Ticketero
REM Requiere: Docker, Java 21+

echo ===============================================
echo   TICKETERO - EJECUCION TESTS E2E
echo ===============================================

set TIMESTAMP=%date:~-4,4%%date:~-10,2%%date:~-7,2%-%time:~0,2%%time:~3,2%%time:~6,2%
set TIMESTAMP=%TIMESTAMP: =0%
set REPORT_DIR=results\e2e-%TIMESTAMP%

echo Creando directorio de resultados: %REPORT_DIR%
mkdir %REPORT_DIR% 2>nul

echo.
echo [1/5] Iniciando contenedores...
docker-compose up -d
timeout /t 30 /nobreak >nul

echo.
echo [2/5] Esperando que la aplicacion este lista...
:wait_app
curl -s http://localhost:8080/actuator/health >nul 2>&1
if errorlevel 1 (
    echo Esperando aplicacion...
    timeout /t 5 /nobreak >nul
    goto wait_app
)
echo ✅ Aplicacion lista

echo.
echo [3/5] Ejecutando tests E2E...
echo Ejecutando BaseIntegrationTestValidation...
call mvn test -Dtest=BaseIntegrationTestValidation -q

echo Ejecutando TicketCreationIT...
call mvn test -Dtest=TicketCreationIT -q

echo Ejecutando TicketProcessingIT...
call mvn test -Dtest=TicketProcessingIT -q

echo Ejecutando NotificationIT...
call mvn test -Dtest=NotificationIT -q

echo Ejecutando ValidationIT...
call mvn test -Dtest=ValidationIT -q

echo Ejecutando AdminDashboardIT...
call mvn test -Dtest=AdminDashboardIT -q

echo.
echo [4/5] Ejecutando todos los tests de integracion...
call mvn test -Dtest="*IT" -Dtest.groups=integration > %REPORT_DIR%\test-output.log 2>&1

echo.
echo [5/5] Generando reporte...
call mvn surefire-report:report -q
copy target\site\surefire-report.html %REPORT_DIR%\ >nul 2>&1

echo.
echo ===============================================
echo   RESUMEN DE EJECUCION
echo ===============================================

REM Extraer resultados del log
findstr /C:"Tests run:" %REPORT_DIR%\test-output.log > temp_results.txt
for /f "tokens=3,5,7,9 delims=:, " %%a in (temp_results.txt) do (
    set TESTS_RUN=%%a
    set FAILURES=%%b
    set ERRORS=%%c
    set SKIPPED=%%d
)
del temp_results.txt >nul 2>&1

echo Tests ejecutados: %TESTS_RUN%
echo Fallos: %FAILURES%
echo Errores: %ERRORS%
echo Omitidos: %SKIPPED%

if "%FAILURES%"=="0" if "%ERRORS%"=="0" (
    echo.
    echo ✅ TODOS LOS TESTS PASARON
    echo.
    echo Reporte disponible en: %REPORT_DIR%\surefire-report.html
) else (
    echo.
    echo ❌ ALGUNOS TESTS FALLARON
    echo Ver detalles en: %REPORT_DIR%\test-output.log
)

echo.
echo [OPCIONAL] Detener contenedores...
set /p STOP_CONTAINERS="¿Detener contenedores? (y/N): "
if /i "%STOP_CONTAINERS%"=="y" (
    docker-compose down
    echo ✅ Contenedores detenidos
)

echo.
echo ===============================================
echo   EJECUCION COMPLETADA
echo ===============================================
pause