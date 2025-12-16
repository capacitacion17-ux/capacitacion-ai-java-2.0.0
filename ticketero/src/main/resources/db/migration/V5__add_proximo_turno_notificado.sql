-- ============================================================================
-- V5: Agregar campo para control de notificación "Próximo Turno"
-- ============================================================================
-- Evita enviar notificaciones duplicadas cuando el ticket se acerca
-- a ser atendido (posición <= 3 en cola)
-- ============================================================================

ALTER TABLE ticket
ADD COLUMN proximo_turno_notificado BOOLEAN DEFAULT FALSE;

-- Índice para consultas de tickets pendientes de notificación
CREATE INDEX idx_ticket_proximo_notificado
ON ticket(queue_type, position_in_queue, proximo_turno_notificado)
WHERE status = 'WAITING' AND proximo_turno_notificado = FALSE;
