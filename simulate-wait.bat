@echo off
echo ========================================
echo   SIMULACION DE ESPERA - 5 MINUTOS
echo ========================================
echo.

echo 1. Iniciando servicios (solo BD y RabbitMQ)...
docker compose up -d postgres rabbitmq

echo.
echo 2. Esperando que los servicios esten listos...
timeout /t 10 /nobreak > nul

echo.
echo 3. Creando ticket de prueba...
curl -X POST http://localhost:8080/api/tickets -H "Content-Type: application/json" -d "{\"nationalId\":\"99887766\",\"telefono\":\"1901537727\",\"branchOffice\":\"Sucursal Demo\",\"queueType\":\"CAJA\"}" > ticket_response.json 2>nul

echo.
echo 4. INICIANDO ESPERA DE 5 MINUTOS...
echo    - Notificacion de creacion enviada
echo    - En 4 minutos recibiras notificacion de "Proximo Turno"
echo    - En 5 minutos recibiras notificacion de "Es tu Turno"
echo.

for /L %%i in (1,1,5) do (
    echo    Minuto %%i de 5... 
    if %%i==4 (
        echo    ^> Enviando notificacion "Proximo Turno"...
        curl -X POST "https://api.telegram.org/bot8323545525:AAG1OvJGJ8Aw8e440W-WiWnvZXKJTzVvT9Y/sendMessage" -H "Content-Type: application/json" -d "{\"chat_id\":\"1901537727\",\"text\":\"⏰ **Proximo Turno**\n\nTu turno sera llamado pronto.\n\nPor favor, estate atento.\"}" > nul 2>&1
    )
    timeout /t 60 /nobreak > nul
)

echo.
echo 5. TIEMPO CUMPLIDO - Procesando ticket...
curl -X POST "https://api.telegram.org/bot8323545525:AAG1OvJGJ8Aw8e440W-WiWnvZXKJTzVvT9Y/sendMessage" -H "Content-Type: application/json" -d "{\"chat_id\":\"1901537727\",\"text\":\"🔔 **¡Es tu turno!**\n\nPor favor acercate al modulo de atencion.\n\nModulo: 1\"}" > nul 2>&1

echo.
echo ========================================
echo   SIMULACION COMPLETADA
echo ========================================
echo Deberias haber recibido 3 notificaciones:
echo 1. Ticket creado
echo 2. Proximo turno (minuto 4)
echo 3. Es tu turno (minuto 5)
echo.
pause