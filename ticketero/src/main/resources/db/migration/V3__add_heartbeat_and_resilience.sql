-- ============================================================================
-- Migración V3: Agregar Heartbeat y Resiliencia al Sistema
-- ============================================================================
-- Propósito: Hacer el sistema resiliente a reinicios y crashes
-- Features: Heartbeat, Auto-recovery, Dead worker detection
-- ============================================================================

-- 1. Agregar columna de heartbeat para detectar workers muertos
ALTER TABLE advisor 
ADD COLUMN last_heartbeat TIMESTAMP;

-- 2. Inicializar heartbeat de todos los asesores activos
UPDATE advisor 
SET last_heartbeat = NOW() 
WHERE status = 'AVAILABLE';

-- 3. Agregar índice para consultas eficientes de heartbeat
CREATE INDEX idx_advisor_last_heartbeat 
ON advisor(last_heartbeat) 
WHERE status = 'BUSY';

-- 4. Agregar columna para rastrear número de reinicios/recuperaciones
ALTER TABLE advisor 
ADD COLUMN recovery_count INTEGER DEFAULT 0;

-- 5. Comentarios para documentación
COMMENT ON COLUMN advisor.last_heartbeat IS 
'Timestamp del último heartbeat del worker. Se actualiza cada 5 segundos. Si > 60s, worker considerado muerto.';

COMMENT ON COLUMN advisor.recovery_count IS 
'Número de veces que este asesor fue auto-recuperado de estado inconsistente.';

-- 6. Agregar tabla de auditoría de recuperaciones
CREATE TABLE recovery_event (
    id BIGSERIAL PRIMARY KEY,
    advisor_id BIGINT REFERENCES advisor(id),
    ticket_id BIGINT REFERENCES ticket(id),
    recovery_type VARCHAR(50) NOT NULL, -- 'DEAD_WORKER', 'TIMEOUT', 'MANUAL'
    old_advisor_status VARCHAR(20),
    old_ticket_status VARCHAR(20),
    detected_at TIMESTAMP NOT NULL DEFAULT NOW(),
    notes TEXT
);

CREATE INDEX idx_recovery_event_advisor ON recovery_event(advisor_id);
CREATE INDEX idx_recovery_event_ticket ON recovery_event(ticket_id);
CREATE INDEX idx_recovery_event_detected ON recovery_event(detected_at);

COMMENT ON TABLE recovery_event IS 
'Auditoría de eventos de recuperación automática del sistema. Registra cuando se detectan y corrigen inconsistencias.';

