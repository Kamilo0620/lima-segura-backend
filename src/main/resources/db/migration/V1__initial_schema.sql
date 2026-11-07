-- V1__initial_schema.sql
-- Lima Segura - Complete schema for automatic incident ingestion pipeline

-- Enable pg_trgm extension for text similarity
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ===================== Users & Auth =====================
CREATE TABLE IF NOT EXISTS usuarios (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ===================== Categories (kept for compatibility) =====================
CREATE TABLE IF NOT EXISTS categorias (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255)
);

-- ===================== Zones (kept for predictions) =====================
CREATE TABLE IF NOT EXISTS zonas (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    district VARCHAR(100) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL
);

-- ===================== Predictions =====================
CREATE TABLE IF NOT EXISTS predicciones (
    id BIGSERIAL PRIMARY KEY,
    zone_id BIGINT NOT NULL REFERENCES zonas(id),
    day_of_week VARCHAR(15) NOT NULL,
    hour INTEGER NOT NULL,
    risk_level VARCHAR(10) NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    generated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ===================== Districts (ubigeo INEI catalog) =====================
CREATE TABLE districts (
    ubigeo VARCHAR(6) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    province VARCHAR(100) NOT NULL,
    department VARCHAR(100) NOT NULL,
    centroid_lat DOUBLE PRECISION NOT NULL,
    centroid_lng DOUBLE PRECISION NOT NULL
);

CREATE INDEX idx_district_name ON districts USING gin (name gin_trgm_ops);
CREATE INDEX idx_district_dept ON districts (department);

-- ===================== News Sources =====================
CREATE TABLE news_sources (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(30) NOT NULL,
    url VARCHAR(500) NOT NULL,
    channel_id VARCHAR(50),
    config TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    credibility DOUBLE PRECISION NOT NULL DEFAULT 8.0,
    poll_interval_minutes INTEGER NOT NULL DEFAULT 5,
    last_polled_at TIMESTAMP,
    consecutive_failures INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(1000)
);

-- ===================== Source Items =====================
CREATE TABLE source_items (
    id BIGSERIAL PRIMARY KEY,
    news_source_id BIGINT NOT NULL REFERENCES news_sources(id),
    external_id VARCHAR(500) NOT NULL,
    url VARCHAR(1000),
    title VARCHAR(500),
    published_at TIMESTAMP,
    content_text TEXT,
    content_hash VARCHAR(64),
    status VARCHAR(20) NOT NULL DEFAULT 'DISCOVERED',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP,
    error VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_source_external UNIQUE (news_source_id, external_id)
);

CREATE INDEX idx_source_item_status ON source_items (status, next_attempt_at);
CREATE INDEX idx_source_item_source ON source_items (news_source_id);

-- ===================== Ingestion Runs =====================
CREATE TABLE ingestion_runs (
    id BIGSERIAL PRIMARY KEY,
    news_source_id BIGINT NOT NULL REFERENCES news_sources(id),
    started_at TIMESTAMP NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMP,
    items_found INTEGER NOT NULL DEFAULT 0,
    items_new INTEGER NOT NULL DEFAULT 0,
    errors INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING'
);

-- ===================== Incidents (new schema) =====================
CREATE TABLE incidents (
    id BIGSERIAL PRIMARY KEY,
    incident_type VARCHAR(30) NOT NULL,
    title VARCHAR(200),
    summary TEXT,
    occurred_at TIMESTAMP,
    occurred_at_precision VARCHAR(15) DEFAULT 'UNKNOWN',
    address VARCHAR(500),
    district_ubigeo VARCHAR(6) REFERENCES districts(ubigeo),
    lat DOUBLE PRECISION,
    lng DOUBLE PRECISION,
    geo_precision VARCHAR(15),
    confidence DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    incident_count INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW',
    origin VARCHAR(20) NOT NULL,
    llm_model VARCHAR(100),
    victims_count INTEGER,
    evidence_excerpt VARCHAR(300),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_incident_occurred_at ON incidents (occurred_at);
CREATE INDEX idx_incident_type_occurred ON incidents (incident_type, occurred_at);
CREATE INDEX idx_incident_district ON incidents (district_ubigeo);
CREATE INDEX idx_incident_status ON incidents (status);
CREATE INDEX idx_incident_title_trgm ON incidents USING gin (title gin_trgm_ops);

-- ===================== Incident Sources (M:N join table) =====================
CREATE TABLE incident_sources (
    incident_id BIGINT NOT NULL REFERENCES incidents(id),
    source_item_id BIGINT NOT NULL REFERENCES source_items(id),
    PRIMARY KEY (incident_id, source_item_id)
);

-- ===================== Zone Subscriptions (optional alerts) =====================
CREATE TABLE zone_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES usuarios(id),
    district_ubigeo VARCHAR(6) NOT NULL REFERENCES districts(ubigeo),
    incident_types VARCHAR(500),
    CONSTRAINT uq_user_district_sub UNIQUE (user_id, district_ubigeo)
);

-- ===================== ShedLock table =====================
CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

-- ===================== Seed: Lima districts (sample) =====================
INSERT INTO districts (ubigeo, name, province, department, centroid_lat, centroid_lng) VALUES
('150101', 'Lima', 'Lima', 'Lima', -12.0464, -77.0428),
('150102', 'Ancon', 'Lima', 'Lima', -11.7262, -77.1486),
('150103', 'Ate', 'Lima', 'Lima', -12.0249, -76.9186),
('150104', 'Barranco', 'Lima', 'Lima', -12.1429, -77.0219),
('150105', 'Breña', 'Lima', 'Lima', -12.0596, -77.0538),
('150106', 'Carabayllo', 'Lima', 'Lima', -11.8548, -77.0369),
('150107', 'Chaclacayo', 'Lima', 'Lima', -11.9822, -76.7614),
('150108', 'Chorrillos', 'Lima', 'Lima', -12.1721, -77.0149),
('150109', 'Cieneguilla', 'Lima', 'Lima', -12.0911, -76.7696),
('150110', 'Comas', 'Lima', 'Lima', -11.9351, -77.0486),
('150111', 'El Agustino', 'Lima', 'Lima', -12.0449, -76.9945),
('150112', 'Independencia', 'Lima', 'Lima', -11.9876, -77.0511),
('150113', 'Jesus Maria', 'Lima', 'Lima', -12.0721, -77.0472),
('150114', 'La Molina', 'Lima', 'Lima', -12.0847, -76.9316),
('150115', 'La Victoria', 'Lima', 'Lima', -12.0677, -77.0179),
('150116', 'Lince', 'Lima', 'Lima', -12.0830, -77.0340),
('150117', 'Los Olivos', 'Lima', 'Lima', -11.9544, -77.0649),
('150118', 'Lurigancho', 'Lima', 'Lima', -11.9833, -76.8833),
('150119', 'Lurin', 'Lima', 'Lima', -12.2699, -76.8739),
('150120', 'Magdalena del Mar', 'Lima', 'Lima', -12.0919, -77.0703),
('150121', 'Pueblo Libre', 'Lima', 'Lima', -12.0755, -77.0619),
('150122', 'Miraflores', 'Lima', 'Lima', -12.1199, -77.0303),
('150123', 'Pachacamac', 'Lima', 'Lima', -12.2307, -76.8622),
('150124', 'Pucusana', 'Lima', 'Lima', -12.4794, -76.7961),
('150125', 'Puente Piedra', 'Lima', 'Lima', -11.8618, -77.0754),
('150126', 'Punta Hermosa', 'Lima', 'Lima', -12.3398, -76.8280),
('150127', 'Punta Negra', 'Lima', 'Lima', -12.3660, -76.7959),
('150128', 'Rimac', 'Lima', 'Lima', -12.0291, -77.0311),
('150129', 'San Bartolo', 'Lima', 'Lima', -12.3832, -76.7819),
('150130', 'San Borja', 'Lima', 'Lima', -12.1063, -77.0031),
('150131', 'San Isidro', 'Lima', 'Lima', -12.0989, -77.0368),
('150132', 'San Juan de Lurigancho', 'Lima', 'Lima', -11.9693, -76.9953),
('150133', 'San Juan de Miraflores', 'Lima', 'Lima', -12.1594, -76.9697),
('150134', 'San Luis', 'Lima', 'Lima', -12.0744, -76.9994),
('150135', 'San Martin de Porres', 'Lima', 'Lima', -11.9892, -77.0691),
('150136', 'San Miguel', 'Lima', 'Lima', -12.0768, -77.0858),
('150137', 'Santa Anita', 'Lima', 'Lima', -12.0431, -76.9694),
('150138', 'Santa Maria del Mar', 'Lima', 'Lima', -12.4018, -76.7879),
('150139', 'Santa Rosa', 'Lima', 'Lima', -11.7951, -77.1650),
('150140', 'Santiago de Surco', 'Lima', 'Lima', -12.1363, -76.9912),
('150141', 'Surquillo', 'Lima', 'Lima', -12.1108, -77.0127),
('150142', 'Villa El Salvador', 'Lima', 'Lima', -12.2061, -76.9354),
('150143', 'Villa Maria del Triunfo', 'Lima', 'Lima', -12.1651, -76.9474),
('070101', 'Callao', 'Callao', 'Callao', -12.0566, -77.1185),
('070102', 'Bellavista', 'Callao', 'Callao', -12.0594, -77.0998),
('070103', 'Carmen de la Legua Reynoso', 'Callao', 'Callao', -12.0462, -77.0959),
('070104', 'La Perla', 'Callao', 'Callao', -12.0743, -77.1105),
('070105', 'La Punta', 'Callao', 'Callao', -12.0697, -77.1568),
('070106', 'Mi Peru', 'Callao', 'Callao', -11.8571, -77.1128),
('070107', 'Ventanilla', 'Callao', 'Callao', -11.8762, -77.1259)
ON CONFLICT (ubigeo) DO NOTHING;

-- ===================== Seed: Default admin user =====================
-- Password: admin123 (BCrypt encoded)
INSERT INTO usuarios (name, email, password, role, created_at) VALUES
('Administrador', 'admin@limasegura.pe', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'ADMIN', NOW())
ON CONFLICT (email) DO NOTHING;

-- ===================== Seed: Sample news sources =====================
INSERT INTO news_sources (name, type, url, channel_id, active, credibility, poll_interval_minutes) VALUES
('ATV Noticias', 'YOUTUBE_CHANNEL', 'https://www.youtube.com/feeds/videos.xml?channel_id=UCuMZJLpbgYMjCpGW2CB4t2A', 'UCuMZJLpbgYMjCpGW2CB4t2A', true, 8.0, 5),
('Canal N', 'YOUTUBE_CHANNEL', 'https://www.youtube.com/feeds/videos.xml?channel_id=UCl0oN0VIzSbbS2BxGcBm_JA', 'UCl0oN0VIzSbbS2BxGcBm_JA', true, 9.3, 5),
('RPP Noticias', 'YOUTUBE_CHANNEL', 'https://www.youtube.com/feeds/videos.xml?channel_id=UCGhVU0D0bCXLT2TJ0L72EDA', 'UCGhVU0D0bCXLT2TJ0L72EDA', true, 9.5, 5),
('El Comercio - Policiales', 'NEWSPAPER_RSS', 'https://elcomercio.pe/arcio/rss/category/lima/', null, true, 9.2, 10),
('La Republica - Sociedad', 'NEWSPAPER_RSS', 'https://larepublica.pe/rss/sociedad', null, true, 8.7, 10)
ON CONFLICT DO NOTHING;
