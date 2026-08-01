-- Adiciona a tabela notification (com.app.notification.Notification), nova em
-- create-migrations e ausente em producao (ver docs/SCHEMA_BASELINE.md). Aplicada
-- normalmente tanto em producao (schema existente, apenas V2 falta) quanto em
-- dev/test (apos V1__baseline.sql). Nenhum DROP/TRUNCATE; tabela nasce vazia.

CREATE TABLE IF NOT EXISTS notification (
    id UUID NOT NULL,
    couple_id UUID NOT NULL,
    recipient_user_id UUID NOT NULL,
    type VARCHAR(255) NOT NULL,
    tmdb_id BIGINT NOT NULL,
    media_type VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    actor_user_id UUID NOT NULL,
    media_track_id UUID,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_notification PRIMARY KEY (id),
    CONSTRAINT fk_notification_couple FOREIGN KEY (couple_id) REFERENCES couples (id)
);

CREATE INDEX IF NOT EXISTS idx_notification_recipient_read ON notification (recipient_user_id, is_read);
