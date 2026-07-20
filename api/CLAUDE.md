# Backend (api/)

Spring Boot (parent 4.1.0) + Java 21, built with the Maven wrapper `./mvnw`.

## Conventions
- **Base Java package is `com.app`** (e.g. `com.app.SessaoADoisApplication`). Do NOT use the old `com.lf.sessao_a_dois`.
- **Maven `groupId` stays `com.lf`** — this is intentional and separate from the Java package. Do not "align" them.
- **Package-by-feature** is the target structure: `com.app.user`, `com.app.couple`, `com.app.media`, `com.app.tracking`, `com.app.match`, `com.app.security`, `com.app.websocket` (see `docs/ARCHITECTURE.md`).
- Testing stack: JUnit 5 + Mockito (+ spring-security-test); Controllers, Services and Repositories require coverage.
- **`com.app.media` (TMDB integration):** `TmdbConfig` exposes a single `RestClient` bean (`tmdbRestClient`) that fails fast (`IllegalStateException`) if `tmdb.api-key` (env `TMDB_API_KEY`) is blank — the bean is eagerly instantiated, so this happens at context startup. Default `language=pt-BR` is injected on every outgoing request via a `ClientHttpRequestInterceptor` (`TmdbDefaultLanguageInterceptor`) that wraps the request with `HttpRequestWrapper` to rewrite the URI — this is the pattern to follow for any other default query param the TMDB client needs. `src/test/resources/application.properties` sets a fake `tmdb.api-key` so `@SpringBootTest` contexts (e.g. `SessaoADoisApplicationTests`) can boot without a real `TMDB_API_KEY` in the environment.
  - Raw TMDB JSON responses are mapped to package-private Java records (e.g. `TmdbMultiSearchItem`) with `@JsonProperty`/`@JsonIgnoreProperties(ignoreUnknown = true)`, then translated into a public simplified DTO (`MediaSearchResult`) — never expose the raw TMDB shape past the service layer.
  - Endpoints in `com.app.media` inherit `anyRequest().authenticated()` from `SecurityConfig` (no explicit `permitAll()` added) — media search/details require a JWT like the rest of the app.
  - To unit-test a service that calls the shared `RestClient` bean, mock it with `Mockito.mock(RestClient.class, RETURNS_DEEP_STUBS)` and stub the terminal `.body(SomeType.class)` call — the fluent `get().uri(...).retrieve()` chain is otherwise painful to mock directly.

## Build / verify
- `./mvnw -DskipTests package` to compile; `./mvnw test` to run tests.
- Note: some sandboxed runtimes lack a JDK and block Maven Central — the build can only be verified where JDK 21 + Maven Central are available.

## Docker
- `Dockerfile` is multi-stage: build (`eclipse-temurin:21-jdk`, uses `./mvnw`) → runtime (`eclipse-temurin:21-jre`, carries only the jar). Build with `docker build -t sessao-api ./api`.
- Layer-cache order matters: copy `.mvn/`, `mvnw`, `pom.xml` and run `dependency:go-offline` **before** `COPY src/`.
- Runtime copies `target/*.jar` → `app.jar`; the glob intentionally excludes the plugin's `.jar.original`. Keep `target/` in `.dockerignore`.
- The app needs a reachable datasource to boot (JPA auto-config), so `docker run` requires `DB_URL` pointing at an accessible PostgreSQL.
