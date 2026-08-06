-- Indices de FK/filtro para as consultas mais frequentes (vinculo do casal,
-- listagem de tracks, likes/rejects pendentes). Nenhuma tabela existente e
-- recriada ou alterada - somente criacao de indice. CREATE INDEX IF NOT EXISTS
-- para ser seguro em re-execucao (dev/test, banco limpo).

CREATE INDEX IF NOT EXISTS idx_couples_user1 ON couples (user1_id);
CREATE INDEX IF NOT EXISTS idx_couples_user2 ON couples (user2_id);

CREATE INDEX IF NOT EXISTS idx_media_track_couple_status ON media_track (couple_id, status);
CREATE INDEX IF NOT EXISTS idx_media_track_couple_tmdb ON media_track (couple_id, tmdb_id);

CREATE INDEX IF NOT EXISTS idx_user_review_track ON user_review (media_track_id);
CREATE INDEX IF NOT EXISTS idx_user_review_user ON user_review (user_id);

CREATE INDEX IF NOT EXISTS idx_match_like_couple_tmdb ON match_like (couple_id, tmdb_id);
CREATE INDEX IF NOT EXISTS idx_match_reject_couple_user ON match_reject (couple_id, user_id);

CREATE INDEX IF NOT EXISTS idx_media_track_genre_track ON media_track_genre (media_track_id);
