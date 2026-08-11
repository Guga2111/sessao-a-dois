# Schema Baseline — Producao (`origin/main` @ e0f3d36)

Base autoritativa para `V1__baseline.sql` (ver `tasks/prd-flyway-migrations.md`, US-001).

## Metodo usado

O PRD previa duas formas de confirmar o schema fisico de producao:

1. `information_schema` do Postgres de producao (Supabase), ou
2. Subir a app em `origin/main` contra um Postgres limpo e capturar o schema gerado pelo Hibernate.

**Nenhuma das duas esta disponivel neste ambiente sandboxed**: sem acesso de rede ao Supabase de producao, e sem `docker`/`java`/`mvn`/`psql` instalados localmente (confirmado: `docker ps`, `which java mvn psql` e `ls /usr/lib/jvm` falham; `api/CLAUDE.md` ja documentava essa limitacao para builds Maven).

Como alternativa, o schema abaixo foi derivado por **analise estatica das entidades JPA** no commit `origin/main` (`e0f3d36`), aplicando as regras de mapeamento padrao do Hibernate 6 (usado pelo Spring Boot 4.1.0) para nomes de coluna, tipos e constraints geradas por `ddl-auto=update`. Adicionalmente:

- `git diff origin/main HEAD -- <cada entidade>` confirmou que `User`, `Couple`, `MediaTrack`, `UserReview`, `MatchLike`, `MatchReject` sao **byte-a-byte identicas** entre `origin/main` e `create-migrations` (HEAD `cc5c464`) — nenhuma mudanca de schema entre os dois pontos para essas seis entidades.
- `git ls-tree -r origin/main` confirmou que `MatchLike.java`/`MatchReject.java` (e portanto `match_like`/`match_reject`) **ja existem em `origin/main`** (nao sao novidade do `create-migrations`), e que `Notification.java` **nao existe** em `origin/main`.

**Este resultado ainda deve ser validado contra o `information_schema` real do Supabase antes do primeiro deploy com Flyway** (rodar a consulta abaixo com acesso ao banco), pois o Hibernate `ddl-auto=update` pode ter deixado o schema fisico levemente diferente do que um `CREATE` limpo geraria (ex.: ordem de colunas, nomes de constraint auto-gerados). A consulta recomendada para essa validacao:

```sql
SELECT table_name, column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'public'
ORDER BY table_name, ordinal_position;

SELECT tc.table_name, tc.constraint_type, tc.constraint_name, kcu.column_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name
WHERE tc.table_schema = 'public';
```

## Tabelas confirmadas em producao (`origin/main`)

`users`, `couples`, `media_track`, `media_track_genre`, `user_review`, `match_like`, `match_reject`.

`notification` **nao existe** em producao — e a unica tabela nova introduzida por `create-migrations` (ver `com.app.notification.Notification`, ausente em `origin/main`).

## Schema derivado por tabela

### `users` (`com.app.user.User`)
| Coluna | Tipo | Constraints |
|---|---|---|
| `id` | UUID | PK |
| `name` | varchar | NOT NULL |
| `email` | varchar | NOT NULL, UNIQUE |
| `password_hash` | varchar | NOT NULL |
| `created_at` | timestamptz (`Instant`) | NOT NULL |

### `couples` (`com.app.couple.Couple`)
| Coluna | Tipo | Constraints |
|---|---|---|
| `id` | UUID | PK |
| `user1_id` | UUID | NOT NULL |
| `user2_id` | UUID | nullable |
| `invite_code` | varchar | NOT NULL, UNIQUE |
| `created_at` | timestamptz (`Instant`) | NOT NULL |

### `media_track` (`com.app.tracking.MediaTrack`)
| Coluna | Tipo | Constraints |
|---|---|---|
| `id` | UUID | PK |
| `couple_id` | UUID | NOT NULL, FK -> `couples.id` |
| `tmdb_id` | bigint | NOT NULL |
| `media_type` | varchar (enum STRING: `MOVIE`,`TV`) | NOT NULL |
| `status` | varchar (enum STRING: `WATCHING`,`WANT_TO_SEE`,`WATCHED`) | NOT NULL |
| `watched_date` | date | nullable |
| `runtime` | integer | nullable |
| `created_at` | timestamp (`LocalDateTime`, `@CreationTimestamp`) | NOT NULL |
| `title` | varchar(255) | nullable (V4) |
| `poster_url` | varchar(500) | nullable (V4) |
| `release_year` | integer | nullable (V4) |

### `media_track_genre` (`@ElementCollection` de `MediaTrack.genreIds`)
| Coluna | Tipo | Constraints |
|---|---|---|
| `media_track_id` | UUID | NOT NULL, FK -> `media_track.id` |
| `genre_id` | integer | NOT NULL |

Sem PK propria (tabela de colecao de elementos simples do Hibernate).

### `user_review` (`com.app.tracking.UserReview`)
| Coluna | Tipo | Constraints |
|---|---|---|
| `id` | UUID | PK |
| `media_track_id` | UUID | NOT NULL, FK -> `media_track.id` |
| `user_id` | UUID | NOT NULL, FK -> `users.id` |
| `rating` | integer | nullable |
| `opinion` | text | nullable |

UNIQUE(`media_track_id`, `user_id`).

### `match_like` (`com.app.match.MatchLike`)
| Coluna | Tipo | Constraints |
|---|---|---|
| `id` | UUID | PK |
| `couple_id` | UUID | NOT NULL, FK -> `couples.id` |
| `user_id` | UUID | NOT NULL |
| `tmdb_id` | bigint | NOT NULL |
| `media_type` | varchar (enum STRING) | NOT NULL |
| `created_at` | timestamp (`LocalDateTime`, `@CreationTimestamp`) | NOT NULL |
| `title` | varchar(255) | nullable (V4) |
| `poster_url` | varchar(500) | nullable (V4) |
| `release_year` | integer | nullable (V4) |

UNIQUE(`couple_id`, `user_id`, `tmdb_id`).

### `match_reject` (`com.app.match.MatchReject`)

Identica em forma a `match_like` (mesmas colunas e mesmo UNIQUE composto).

### `refresh_token` (`com.app.auth.RefreshToken`, V5)

Nova em V5, epico 4 (migracao de autenticacao). Guarda apenas o hash do refresh
token (nunca o valor em claro) e a cadeia de substituicao usada na rotacao e na
deteccao de reuso.

| Coluna | Tipo | Constraints |
|---|---|---|
| `id` | UUID | PK |
| `user_id` | UUID | NOT NULL, FK -> `users.id` |
| `token_hash` | varchar(64) | NOT NULL, UNIQUE (SHA-256 em hex) |
| `expires_at` | timestamptz (`Instant`) | NOT NULL |
| `revoked_at` | timestamptz (`Instant`) | nullable |
| `replaced_by_id` | UUID | nullable, FK -> `refresh_token.id` |
| `user_agent` | varchar(255) | nullable |
| `ip` | varchar(45) | nullable (cabe IPv6) |
| `created_at` | timestamptz (`Instant`) | NOT NULL |

Indices `idx_refresh_token_user` (`user_id`) e `idx_refresh_token_expires` (`expires_at`).

### `password_reset_token` (`com.app.auth.PasswordResetToken`, V8)

Nova em V8, epico 10 (recuperacao de senha e e-mail transacional). Guarda apenas
o hash do token de redefinicao (nunca o valor em claro), no mesmo espirito do
`refresh_token` acima.

| Coluna | Tipo | Constraints |
|---|---|---|
| `id` | UUID | PK |
| `user_id` | UUID | NOT NULL, FK -> `users.id` |
| `token_hash` | varchar(64) | NOT NULL, UNIQUE (SHA-256 em hex) |
| `expires_at` | timestamptz (`Instant`) | NOT NULL |
| `used_at` | timestamptz (`Instant`) | nullable |
| `created_at` | timestamptz (`Instant`) | NOT NULL |

Indices `idx_password_reset_token_user` (`user_id`) e
`idx_password_reset_token_expires` (`expires_at`).

## Fora do escopo do V1 (documentado para referencia do V2)

`notification` (`com.app.notification.Notification`) — nova em `create-migrations`, nao existe em producao:

| Coluna | Tipo | Constraints |
|---|---|---|
| `id` | UUID | PK |
| `couple_id` | UUID | NOT NULL, FK -> `couples.id` |
| `recipient_user_id` | UUID | NOT NULL |
| `type` | varchar (enum STRING) | NOT NULL |
| `tmdb_id` | bigint | NOT NULL |
| `media_type` | varchar (enum STRING) | NOT NULL |
| `title` | varchar | NOT NULL |
| `actor_user_id` | UUID | NOT NULL |
| `media_track_id` | UUID | nullable |
| `is_read` | boolean | NOT NULL |
| `created_at` | timestamptz (`Instant`, `@CreationTimestamp`) | NOT NULL |

Indice `idx_notification_recipient_read` sobre (`recipient_user_id`, `is_read`).
