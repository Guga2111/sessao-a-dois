-- Adiciona expiracao ao codigo de convite (com.app.couple.Couple), epico 5
-- (US-007/008/009/010): o ciclo de vida completo do convite passa a ter TTL.
--
-- Decisao: ao ser usado (join bem-sucedido), o invite_code sera LIMPO (setado
-- para NULL), nao apenas marcado como usado em campo separado - por isso a
-- coluna precisa se tornar nullable aqui. Postgres permite varios NULL num
-- unique constraint (cada NULL e distinto para fins de unicidade), entao
-- limpar o codigo de varios casais pareados nao colide com a
-- UniqueConstraint(invite_code) ja existente. Um campo "usado" separado exigiria
-- mais uma coluna so para isso; limpar reaproveita a coluna existente e deixa
-- o invariante simples de auditar: invite_code != NULL significa "convite
-- ainda ativo (pode ou nao estar expirado)".
--
-- Apenas ADD COLUMN IF NOT EXISTS e ALTER COLUMN DROP NOT NULL (idempotente),
-- sem DROP/TRUNCATE. Casais existentes ficam com invite_code_expires_at NULL,
-- tratado pela aplicacao como "nao expira".

ALTER TABLE couples ADD COLUMN IF NOT EXISTS invite_code_expires_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE couples ALTER COLUMN invite_code DROP NOT NULL;
