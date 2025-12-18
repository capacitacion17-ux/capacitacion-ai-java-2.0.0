-- ============================================================================
-- Migración V7: Optimización de Índices para Mejor Performance
-- ============================================================================
-- Propósito: Agregar índices optimizados basados en patrones de consulta
-- ============================================================================

-- 1. Índices compuestos para consultas frecuentes de tickets
CREATE INDEX IF NOT EXISTS idx_ticket_status_queue_created 
ON ticket(status, queue_type, created_at) 
WHERE status IN ('WAITING', 'CALLED', 'IN_PROGRESS');

-- 2. Índice para búsquedas por rango de fechas (reportes)
CREATE INDEX IF NOT EXISTS idx_ticket_created_at_date 
ON ticket(DATE(created_at), queue_type);

-- 3. Índice para consultas de posición en cola
CREATE INDEX IF NOT EXISTS idx_ticket_queue_position_status 
ON ticket(queue_type, position_in_queue, status) 
WHERE position_in_queue IS NOT NULL;

-- 4. Índice para asesores activos con heartbeat
CREATE INDEX IF NOT EXISTS idx_advisor_active_heartbeat 
ON advisor(status, last_heartbeat) 
WHERE status IN ('AVAILABLE', 'BUSY');

-- 5. Índice para eventos de ticket por fecha (auditoría)
CREATE INDEX IF NOT EXISTS idx_ticket_event_created_type 
ON ticket_event(created_at, event_type);

-- 6. Índice para recovery events
CREATE INDEX IF NOT EXISTS idx_recovery_event_detected 
ON recovery_event(detected_at DESC, recovery_type);

-- 7. Índice parcial para tickets activos (no completados)
CREATE INDEX IF NOT EXISTS idx_ticket_active_branch 
ON ticket(branch_office, queue_type, created_at) 
WHERE status NOT IN ('COMPLETED', 'CANCELLED');

-- 8. Índice para búsquedas por national_id con queue_type
CREATE INDEX IF NOT EXISTS idx_ticket_national_id_queue 
ON ticket(national_id, queue_type, created_at DESC);

-- 10. Estadísticas actualizadas para el optimizador
ANALYZE ticket;
ANALYZE advisor;
ANALYZE ticket_event;
ANALYZE recovery_event;

-- 9. Estadísticas actualizadas para el optimizador
ANALYZE ticket;
ANALYZE advisor;
ANALYZE ticket_event;
ANALYZE recovery_event;

-- 10. Comentarios para documentación
COMMENT ON INDEX idx_ticket_status_queue_created IS 
'Índice compuesto para consultas de tickets por estado y cola, ordenados por fecha';

COMMENT ON INDEX idx_ticket_queue_position_status IS 
'Índice para cálculos eficientes de posición en cola';

COMMENT ON INDEX idx_advisor_active_heartbeat IS 
'Índice para detección rápida de asesores activos y heartbeat';