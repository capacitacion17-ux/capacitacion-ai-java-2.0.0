-- ========================================
-- MIGRATION: V6__drop_legacy_mensaje_table.sql
-- DESCRIPTION: Remove legacy mensaje table (replaced by Outbox Pattern)
-- ========================================

-- Drop indexes first
DROP INDEX IF EXISTS idx_mensaje_ticket_id;
DROP INDEX IF EXISTS idx_mensaje_estado_envio;
DROP INDEX IF EXISTS idx_mensaje_fecha_programada;

-- Drop the legacy table
DROP TABLE IF EXISTS mensaje;

-- Note: The messaging functionality is now handled by:
-- 1. outbox_message table (transactional outbox pattern)
-- 2. TelegramService (direct notification)
-- 3. NotificationService (notification orchestration)
