@echo off
echo ========================================
echo   SIMULACION DE ESPERA - 5 MINUTOS REALES
echo ========================================
echo.

echo Paso 1: Enviando notificacion de "Ticket Creado"...
curl -s -X POST "https://api.telegram.org/bot8323545525:AAG1OvJGJ8Aw8e440W-WiWnvZXKJTzVvT9Y/sendMessage" -H "Content-Type: application/json" -d "{\"chat_id\":\"1901537727\",\"text\":\"✅ **Ticket Creado**\n\nTu numero de turno es: **C999**\n\nTe notificaremos cuando estes proximo a ser atendido.\",\"parse_mode\":\"Markdown\"}" > nul

echo ✅ Notificacion 1/3 enviada
echo.
echo Esperando 4 minutos para "Proximo Turno"...

REM Esperar 4 minutos (240 segundos)
for /L %%i in (1,1,4) do (
    echo    Minuto %%i de 4...
    timeout /t 60 /nobreak > nul
)

echo.
echo Paso 2: Enviando notificacion de "Proximo Turno"...
curl -s -X POST "https://api.telegram.org/bot8323545525:AAG1OvJGJ8Aw8e440W-WiWnvZXKJTzVvT9Y/sendMessage" -H "Content-Type: application/json" -d "{\"chat_id\":\"1901537727\",\"text\":\"⏰ **Proximo Turno**\n\nTu turno **C999** sera llamado pronto.\n\nPor favor, estate atento.\",\"parse_mode\":\"Markdown\"}" > nul

echo ✅ Notificacion 2/3 enviada
echo.
echo Esperando 1 minuto final...
timeout /t 60 /nobreak > nul

echo.
echo Paso 3: Enviando notificacion de "Es tu Turno"...
curl -s -X POST "https://api.telegram.org/bot8323545525:AAG1OvJGJ8Aw8e440W-WiWnvZXKJTzVvT9Y/sendMessage" -H "Content-Type: application/json" -d "{\"chat_id\":\"1901537727\",\"text\":\"🔔 **¡Es tu turno!**\n\nTurno **C999**, por favor acercate al modulo de atencion.\n\n📍 **Modulo: 1**\",\"parse_mode\":\"Markdown\"}" > nul

echo ✅ Notificacion 3/3 enviada
echo.
echo ========================================
echo   ✅ SIMULACION COMPLETADA
echo ========================================
echo.
echo Has recibido las 3 notificaciones:
echo 1. ✅ Ticket creado (inicio)
echo 2. ⏰ Proximo turno (minuto 4)
echo 3. 🔔 Es tu turno (minuto 5)
echo.
echo Total de espera: 5 minutos reales
echo.
pause