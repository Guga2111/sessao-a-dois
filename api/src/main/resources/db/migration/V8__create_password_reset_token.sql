-- Cria a tabela password_reset_token (com.app.auth.PasswordResetToken), epico 10
-- (recuperacao de senha). Guarda apenas o hash SHA-256 do token de reset (nunca o
-- valor em claro), com expiracao curta e uso unico (used_at). Apenas CREATE
-- TABLE/INDEX IF NOT EXISTS, sem DROP/TRUNCATE e sem default de banco em id
-- (@GeneratedValue(strategy = UUID) gera client-side), mesmo molde da V5
-- (refresh_token).

CREATE TABLE IF NOT EXISTS password_reset_token (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_password_reset_token PRIMARY KEY (id),
    CONSTRAINT uk_password_reset_token_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_token_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX IF NOT EXISTS idx_password_reset_token_user ON password_reset_token (user_id);
CREATE INDEX IF NOT EXISTS idx_password_reset_token_expires ON password_reset_token (expires_at);
