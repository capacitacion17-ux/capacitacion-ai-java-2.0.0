-- ========================================
-- MIGRATION: V1__create_tables.sql
-- DESCRIPCIÓN: Crea las tablas ticket y mensaje
-- ========================================

-- Tabla: ticket
CREATE TABLE ticket (
    id BIGSERIAL PRIMARY KEY,
    codigo_referencia UUID UNIQUE NOT NULL,
    national_id VARCHAR(20) NOT NULL,
    telefono VARCHAR(15),
    branch_office VARCHAR(100) NOT NULL,
    numero VARCHAR(4) NOT NULL,
    queue VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Índices para ticket
CREATE INDEX idx_ticket_codigo_referencia ON ticket(codigo_referencia);
CREATE INDEX idx_ticket_national_id ON ticket(national_id);
CREATE INDEX idx_ticket_created_at ON ticket(created_at);

-- Tabla: mensaje
CREATE TABLE mensaje (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    plantilla VARCHAR(50) NOT NULL,
    medio_envio VARCHAR(20) NOT NULL DEFAULT 'TELEGRAM',
    estado_envio VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    fecha_programada TIMESTAMP NOT NULL,
    fecha_envio TIMESTAMP,
    telegram_message_id VARCHAR(50),
    intentos INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_mensaje_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id) ON DELETE CASCADE
);

-- Índices para mensaje
CREATE INDEX idx_mensaje_ticket_id ON mensaje(ticket_id);
CREATE INDEX idx_mensaje_estado_envio ON mensaje(estado_envio);
CREATE INDEX idx_mensaje_fecha_programada ON mensaje(fecha_programada);




