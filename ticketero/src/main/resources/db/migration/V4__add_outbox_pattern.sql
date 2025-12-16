-- ============================================================================
-- V4: Implementación del Patrón Outbox para Coordinación Transaccional
-- ============================================================================
-- Este patrón garantiza consistencia entre PostgreSQL y RabbitMQ:
-- - Los mensajes se guardan en la misma TX que los datos de negocio
-- - Un scheduler separado publica a RabbitMQ y marca como enviados
-- - Si el scheduler falla, los mensajes pendientes se reintentan
-- ============================================================================

-- Tabla de mensajes outbox
CREATE TABLE outbox_message (
    id BIGSERIAL PRIMARY KEY,

    -- Identificación del agregado
    aggregate_type VARCHAR(50) NOT NULL,      -- 'TICKET', 'ADVISOR', etc.
    aggregate_id BIGINT NOT NULL,             -- ID del agregado (ej: ticket.id)

    -- Tipo de evento
    event_type VARCHAR(50) NOT NULL,          -- 'TICKET_CREATED', 'TICKET_UPDATED', etc.

    -- Datos del mensaje (JSON)
    payload JSONB NOT NULL,                   -- Contenido completo del mensaje
    routing_key VARCHAR(100) NOT NULL,        -- Cola destino (ej: 'caja-queue')

    -- Estado de procesamiento
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,

    -- Control de reintentos
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 5,
    next_retry_at TIMESTAMP,
    error_message TEXT,

    -- Constraint para valores válidos de status
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED'))
);

-- Índice para búsqueda eficiente de mensajes pendientes
-- Parcial: solo indexa PENDING para mejor rendimiento
CREATE INDEX idx_outbox_pending
    ON outbox_message(status, created_at)
    WHERE status = 'PENDING';

-- Índice para búsqueda por agregado (útil para debugging y auditoría)
CREATE INDEX idx_outbox_aggregate
    ON outbox_message(aggregate_type, aggregate_id);

-- Índice para limpieza de mensajes antiguos
CREATE INDEX idx_outbox_processed
    ON outbox_message(processed_at)
    WHERE status = 'SENT';

-- ============================================================================
-- Migración de Advisor: Eliminar relación circular current_ticket_id
-- ============================================================================
-- La relación bidireccional Advisor.currentTicket <-> Ticket.assignedAdvisor
-- causa problemas de sincronización. Eliminamos un lado de la relación.
-- El ticket actual de un advisor se puede obtener por query:
--   SELECT * FROM ticket WHERE assigned_advisor_id = ? AND status IN ('CALLED', 'IN_PROGRESS')
-- ============================================================================

-- Paso 1: Eliminar la constraint FK si existe
ALTER TABLE advisor
    DROP CONSTRAINT IF EXISTS fk_advisor_current_ticket;

-- Paso 2: Eliminar la columna current_ticket_id
ALTER TABLE advisor
    DROP COLUMN IF EXISTS current_ticket_id;

-- ============================================================================
-- Comentarios para documentación
-- ============================================================================
COMMENT ON TABLE outbox_message IS 'Tabla para patrón Outbox - garantiza consistencia entre DB y RabbitMQ';
COMMENT ON COLUMN outbox_message.aggregate_type IS 'Tipo de entidad origen (TICKET, ADVISOR, etc.)';
COMMENT ON COLUMN outbox_message.aggregate_id IS 'ID de la entidad origen';
COMMENT ON COLUMN outbox_message.event_type IS 'Tipo de evento (TICKET_CREATED, etc.)';
COMMENT ON COLUMN outbox_message.payload IS 'Contenido JSON del mensaje para RabbitMQ';
COMMENT ON COLUMN outbox_message.routing_key IS 'Cola destino en RabbitMQ';
COMMENT ON COLUMN outbox_message.status IS 'PENDING=por enviar, PROCESSING=enviando, SENT=enviado, FAILED=error permanente';
