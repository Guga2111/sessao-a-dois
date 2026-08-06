-- Desnormaliza titulo/poster/ano em media_track e match_like para que a
-- leitura desses dados nunca precise ir ao TMDB (US-002 do epico 3, TMDB
-- resiliencia e latencia). Apenas ADD COLUMN IF NOT EXISTS, sem DROP/TRUNCATE
-- e sem default no lado do banco - quem popula essas colunas e a aplicacao
-- (US-003), nao a migration.

ALTER TABLE media_track ADD COLUMN IF NOT EXISTS title VARCHAR(255);
ALTER TABLE media_track ADD COLUMN IF NOT EXISTS poster_url VARCHAR(500);
ALTER TABLE media_track ADD COLUMN IF NOT EXISTS release_year INTEGER;

ALTER TABLE match_like ADD COLUMN IF NOT EXISTS title VARCHAR(255);
ALTER TABLE match_like ADD COLUMN IF NOT EXISTS poster_url VARCHAR(500);
ALTER TABLE match_like ADD COLUMN IF NOT EXISTS release_year INTEGER;
