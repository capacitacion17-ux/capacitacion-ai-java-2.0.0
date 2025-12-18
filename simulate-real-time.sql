-- Script para simular tiempos reales de espera
-- Configurar tiempo de servicio en segundos para simulación

-- Actualizar configuración de colas para usar segundos en lugar de minutos
UPDATE queue_config SET avg_service_time_minutes = 300 WHERE queue_type = 'CAJA'; -- 5 minutos = 300 segundos
UPDATE queue_config SET avg_service_time_minutes = 600 WHERE queue_type = 'PERSONAL'; -- 10 minutos = 600 segundos

-- Verificar asesores disponibles
SELECT id, name, status, module_number FROM advisor WHERE status = 'AVAILABLE';