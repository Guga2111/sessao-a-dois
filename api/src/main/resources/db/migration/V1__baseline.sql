-- Baseline do schema real de producao (origin/main @ e0f3d36), gerado via
-- ddl-auto=update. Derivado por analise estatica das entidades JPA
-- (ver docs/SCHEMA_BASELINE.md). Em producao, baseline-on-migrate + baseline-version=1
-- fazem o Flyway RECONHECER este arquivo sem executa-lo (o schema ja existe);
-- em dev/test (banco limpo) ele e executado normalmente. Nao contem a tabela
-- notification (ver V2__create_notification.sql).

CREATE TABLE IF NOT EXISTS users (
    id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS couples (
    id UUID NOT NULL,
    user1_id UUID NOT NULL,
    user2_id UUID,
    invite_code VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_couples PRIMARY KEY (id),
    CONSTRAINT uk_couples_invite_code UNIQUE (invite_code)
);

CREATE TABLE IF NOT EXISTS media_track (
    id UUID NOT NULL,
    couple_id UUID NOT NULL,
    tmdb_id BIGINT NOT NULL,
    media_type VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    watched_date DATE,
    runtime INTEGER,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_media_track PRIMARY KEY (id),
    CONSTRAINT fk_media_track_couple FOREIGN KEY (couple_id) REFERENCES couples (id)
);

CREATE TABLE IF NOT EXISTS media_track_genre (
    media_track_id UUID NOT NULL,
    genre_id INTEGER NOT NULL,
    CONSTRAINT fk_media_track_genre_media_track FOREIGN KEY (media_track_id) REFERENCES media_track (id)
);

CREATE TABLE IF NOT EXISTS user_review (
    id UUID NOT NULL,
    media_track_id UUID NOT NULL,
    user_id UUID NOT NULL,
    rating INTEGER,
    opinion TEXT,
    CONSTRAINT pk_user_review PRIMARY KEY (id),
    CONSTRAINT uk_user_review_media_track_user UNIQUE (media_track_id, user_id),
    CONSTRAINT fk_user_review_media_track FOREIGN KEY (media_track_id) REFERENCES media_track (id),
    CONSTRAINT fk_user_review_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS match_like (
    id UUID NOT NULL,
    couple_id UUID NOT NULL,
    user_id UUID NOT NULL,
    tmdb_id BIGINT NOT NULL,
    media_type VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_match_like PRIMARY KEY (id),
    CONSTRAINT uk_match_like_couple_user_tmdb UNIQUE (couple_id, user_id, tmdb_id),
    CONSTRAINT fk_match_like_couple FOREIGN KEY (couple_id) REFERENCES couples (id)
);

CREATE TABLE IF NOT EXISTS match_reject (
    id UUID NOT NULL,
    couple_id UUID NOT NULL,
    user_id UUID NOT NULL,
    tmdb_id BIGINT NOT NULL,
    media_type VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_match_reject PRIMARY KEY (id),
    CONSTRAINT uk_match_reject_couple_user_tmdb UNIQUE (couple_id, user_id, tmdb_id),
    CONSTRAINT fk_match_reject_couple FOREIGN KEY (couple_id) REFERENCES couples (id)
);
