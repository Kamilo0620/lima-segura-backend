-- V5: reportes ciudadanos con autor y confirmaciones comunitarias
-- Un ciudadano reporta un incidente (queda en PENDING_REVIEW) y otros usuarios lo confirman.
-- Con 3 confirmaciones el incidente pasa a PUBLISHED.

ALTER TABLE incidents ADD COLUMN IF NOT EXISTS reporter_id BIGINT REFERENCES usuarios(id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS incident_confirmations (
    id BIGSERIAL PRIMARY KEY,
    incident_id BIGINT NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_confirmation_incident_user UNIQUE (incident_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_confirmation_incident ON incident_confirmations (incident_id);
