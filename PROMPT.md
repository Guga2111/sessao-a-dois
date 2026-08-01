# Migracao de ddl-auto=update para Flyway com dados em producao

## Contexto do Projeto

- **Aplicacao:** Sessao a Dois — app para casais gerenciarem filmes/series (Spring Boot 4.1.0, Java 21, PostgreSQL no Supabase)
- **Dominio de producao:** `sessaoadois.luisgosampaio.com`
- **Estado atual:** A aplicacao foi deployada **uma unica vez**, usando `spring.jpa.hibernate.ddl-auto=update` (sem migrations). **Ja existem usuarios ativos com dados reais no banco.**
- **Branch `main`:** Contem exatamente o codigo que esta em producao (137 commits, ultimo: `e0f3d36 Merge pull request #13 from Guga2111/dev`). O schema do banco de producao corresponde as entidades JPA nesse commit.
- **Branch `create-migrations` (atual):** Contem **50 commits** apos a `main`, incluindo:
  - Novas entidades: `MatchLike`, `MatchReject`, `Notification`, e possivelmente outras
  - Modificacoes em entidades existentes: `MediaTrack` ganhou `genreIds` (`@ElementCollection` -> tabela `media_track_genre`), `Notification` tem campo nullable `media_track_id`, entre outras
  - Novos endpoints, services, e logica de negocio

## O que preciso

1. **Navegar pelo historico git** comparando as entidades JPA da branch `main` (estado deployado/producao) com o estado atual na branch `comparison`, para identificar **todas** as diferencas de schema:
   - Tabelas novas
   - Colunas adicionadas a tabelas existentes
   - Tabelas auxiliares (`@ElementCollection`, `@JoinTable`, etc.)
   - Constraints, indices, ou alteracoes de tipo

2. **Integrar o Flyway** ao projeto Spring Boot:
   - Adicionar a dependencia no `pom.xml`
   - Configurar no `application.properties` (substituir `ddl-auto=update` por `ddl-auto=validate` em producao)
   - Criar o diretorio `db/migration` com os scripts SQL versionados

3. **Gerar os scripts de migracao SQL** na ordem correta:
   - `V1__baseline.sql` — **NAO deve ser executado em producao** (o schema ja existe). Usar `flyway_schema_history` com baseline ou outro mecanismo para que o Flyway entenda que V1 ja foi aplicado.
   - `V2__xxx.sql`, `V3__xxx.sql`, etc. — Scripts incrementais com as alteracoes de schema entre `main` e `comparison`, na ordem cronologica correta dos commits. Cada migration deve ser idempotente-safe e considerar que **ha dados existentes** (usar `ALTER TABLE ... ADD COLUMN ... DEFAULT ...`, `CREATE TABLE IF NOT EXISTS`, etc. conforme necessario).

4. **Configurar o baseline do Flyway** para que no proximo deploy em producao:
   - O Flyway reconheca que o schema de V1 ja existe (via `spring.flyway.baseline-on-migrate=true` ou `spring.flyway.baseline-version=1`)
   - Aplique apenas V2+ automaticamente

## Restricoes importantes

- **Dados em producao NAO podem ser perdidos.** Nenhum `DROP TABLE`, `DROP COLUMN`, ou `TRUNCATE` sem confirmacao explicita.
- **Antes de modificar qualquer um destes arquivos, me avise e explique o que pretende mudar — NAO altere diretamente sem minha aprovacao:**
  - `scripts/deploy.sh`
  - `api/Dockerfile`
  - `docker-compose-prod.yml`
- O banco PostgreSQL roda no **Supabase** (externo, conexao via connection pooler na porta 6543 com `?prepareThreshold=0`).
- O `application.properties` de teste (`src/test/resources/`) precisa ser mantido funcional — mirror de qualquer nova propriedade do Flyway.
- O ambiente de dev local usa `docker-compose-dev.yml` com PostgreSQL local — o Flyway deve funcionar tanto em dev quanto em prod.

## Entregaveis esperados

1. Lista completa das diferencas de schema (main -> comparison)
2. Dependencia Flyway no `pom.xml`
3. Configuracao no `application.properties` (main e test)
4. Scripts SQL de migracao versionados (`V1__baseline.sql`, `V2__...`, etc.)
5. Estrategia de baseline para producao (explicada)
6. Se necessario: lista de alteracoes propostas para `deploy.sh` / `Dockerfile` / `docker-compose-prod.yml` (para aprovacao)
