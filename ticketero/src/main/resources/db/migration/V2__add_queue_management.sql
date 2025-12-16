-- V2: Agregar tablas para gestión real de turnos con asesores y colas dinámicas

-- ============================================================
-- TABLA: advisor (Asesores)
-- ============================================================
CREATE TABLE advisor (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    module_number INTEGER NOT NULL,
    queue_types VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL,
    current_ticket_id BIGINT,
    avg_service_time_minutes INTEGER DEFAULT 5,
    total_tickets_served INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_active_at TIMESTAMP
);

CREATE INDEX idx_advisor_status ON advisor(status);
CREATE INDEX idx_advisor_module ON advisor(module_number);

-- ============================================================
-- TABLA: queue_config (Configuración por cola)
-- ============================================================
CREATE TABLE queue_config (
    id BIGSERIAL PRIMARY KEY,
    queue_type VARCHAR(50) UNIQUE NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    avg_service_time_minutes INTEGER NOT NULL DEFAULT 5,
    notification_threshold INTEGER NOT NULL DEFAULT 3,
    priority INTEGER NOT NULL DEFAULT 1,
    max_queue_size INTEGER,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================================
-- MODIFICAR TABLA: ticket
-- ============================================================

-- Agregar nuevas columnas
ALTER TABLE ticket 
    ADD COLUMN queue_type VARCHAR(50),
    ADD COLUMN position_in_queue INTEGER,
    ADD COLUMN estimated_wait_minutes INTEGER,
    ADD COLUMN assigned_advisor_id BIGINT,
    ADD COLUMN assigned_module_number INTEGER,
    ADD COLUMN called_at TIMESTAMP,
    ADD COLUMN started_at TIMESTAMP,
    ADD COLUMN completed_at TIMESTAMP;

-- Migrar datos existentes: copiar 'queue' a 'queue_type'
UPDATE ticket SET queue_type = queue WHERE queue_type IS NULL;

-- Ahora hacer NOT NULL
ALTER TABLE ticket ALTER COLUMN queue_type SET NOT NULL;

-- Hacer la columna 'queue' nullable (ya no se usa, reemplazada por queue_type)
ALTER TABLE ticket ALTER COLUMN queue DROP NOT NULL;

-- Actualizar status existentes a nuevo formato
UPDATE ticket SET status = 'WAITING' WHERE status = 'CREATED';

-- ============================================================
-- TABLA: ticket_event (Auditoría de eventos)
-- ============================================================
CREATE TABLE ticket_event (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    old_status VARCHAR(20),
    new_status VARCHAR(20),
    old_position INTEGER,
    new_position INTEGER,
    advisor_id BIGINT,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_event_ticket ON ticket_event(ticket_id);
CREATE INDEX idx_event_created ON ticket_event(created_at);

-- ============================================================
-- FOREIGN KEYS
-- ============================================================

-- ticket -> advisor
ALTER TABLE ticket 
    ADD CONSTRAINT fk_ticket_advisor 
    FOREIGN KEY (assigned_advisor_id) 
    REFERENCES advisor(id);

-- advisor -> ticket (current)
ALTER TABLE advisor 
    ADD CONSTRAINT fk_advisor_current_ticket 
    FOREIGN KEY (current_ticket_id) 
    REFERENCES ticket(id);

-- ticket_event -> ticket
ALTER TABLE ticket_event
    ADD CONSTRAINT fk_event_ticket 
    FOREIGN KEY (ticket_id) 
    REFERENCES ticket(id);

-- ticket_event -> advisor
ALTER TABLE ticket_event
    ADD CONSTRAINT fk_event_advisor 
    FOREIGN KEY (advisor_id) 
    REFERENCES advisor(id);

-- ============================================================
-- ÍNDICES ADICIONALES
-- ============================================================
CREATE INDEX idx_ticket_queue_status ON ticket(queue_type, status);
CREATE INDEX idx_ticket_position ON ticket(queue_type, position_in_queue);

-- ============================================================
-- DATOS INICIALES: Configuración de colas
-- ============================================================
INSERT INTO queue_config (queue_type, display_name, avg_service_time_minutes, notification_threshold) VALUES
('CAJA', 'Caja', 5, 3),
('PERSONAL', 'Banca Personal', 10, 3),
('EMPRESAS', 'Banca Empresas', 15, 2),
('GERENCIA', 'Atención Gerencial', 20, 1);

-- ============================================================
-- DATOS INICIALES: Asesores de ejemplo
-- ============================================================
INSERT INTO advisor (name, email, module_number, queue_types, status) VALUES
('María López', 'maria.lopez@banco.com', 1, '["CAJA"]', 'AVAILABLE'),
('Juan Pérez', 'juan.perez@banco.com', 2, '["CAJA"]', 'AVAILABLE'),
('Ana García', 'ana.garcia@banco.com', 3, '["CAJA", "PERSONAL"]', 'AVAILABLE'),
('Carlos Ruiz', 'carlos.ruiz@banco.com', 4, '["PERSONAL"]', 'AVAILABLE'),
('Laura Martínez', 'laura.martinez@banco.com', 5, '["EMPRESAS"]', 'AVAILABLE');


