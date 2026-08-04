-- Cria a tabela refresh_token (com.app.auth.RefreshToken), epico 4 (migracao de
-- autenticacao). O servidor passa a poder revogar sessoes: hash do token (nunca
-- o valor em claro), expiracao, revogacao e cadeia de substituicao por rotacao
-- (replaced_by_id). Apenas CREATE TABLE/INDEX IF NOT EXISTS, sem DROP/TRUNCATE
-- e sem default de banco em id (@GeneratedValue(strategy = UUID) gera client-side).

CREATE TABLE IF NOT EXISTS refresh_token (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    replaced_by_id UUID,
    user_agent VARCHAR(255),
    ip VARCHAR(45),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_refresh_token PRIMARY KEY (id),
    CONSTRAINT uk_refresh_token_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_refresh_token_replaced_by FOREIGN KEY (replaced_by_id) REFERENCES refresh_token (id)
);

CREATE INDEX IF NOT EXISTS idx_refresh_token_user ON refresh_token (user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_token_expires ON refresh_token (expires_at);
