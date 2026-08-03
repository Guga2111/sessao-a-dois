package com.app.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @SpringBootTest puro (nao @DataJpaTest) para nao sofrer o PropertyMappingContextCustomizer,
 * que forca um H2 sem MODE=PostgreSQL independente do que este @TestPropertySource declarar.
 *
 * Local/sandbox (sem docker/postgres): DB_URL nao esta definida no ambiente, entao o Flyway
 * roda contra H2 em MODE=PostgreSQL, exercitando a sintaxe Postgres das migrations.
 * CI (.github/workflows/ci.yml): DB_URL aponta para o service postgres:17-alpine real, entao
 * o mesmo teste valida as migrations contra Postgres de verdade.
 *
 * O contexto so sobe se o Flyway aplicar V1, V2 (e qualquer migration futura) com sucesso E
 * se o Hibernate (ddl-auto=validate) nao encontrar nenhuma divergencia entre as entidades JPA
 * e o schema resultante - a asserção sobre `flyway.info()` abaixo é redundante com esse boot
 * bem-sucedido, mas torna explícito o que está sendo verificado (nenhuma migration pulada ou
 * marcada como falha) em vez de depender só do contexto ter subido.
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${DB_URL:jdbc:h2:mem:migrations;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE}",
        "spring.datasource.username=${DB_USER:sa}",
        "spring.datasource.password=${DB_PASSWORD:}",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class FlywayMigrationTest {

    @Autowired
    private Flyway flyway;

    @Test
    void migrationsApplyInSequenceAndEntitiesMatchSchema() {
        MigrationInfo[] applied = flyway.info().applied();

        assertThat(applied)
                .extracting(info -> info.getVersion().toString())
                .as("todas as migrations de db/migration devem ter sido aplicadas em sequencia")
                .contains("1", "2", "3");

        assertThat(applied)
                .as("nenhuma migration aplicada pode estar em estado diferente de sucesso")
                .extracting(MigrationInfo::getState)
                .containsOnly(MigrationState.SUCCESS);
    }

}
