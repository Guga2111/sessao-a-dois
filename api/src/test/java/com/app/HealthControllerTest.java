package com.app;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.app.security.ClientIpResolver;
import com.app.security.JwtService;
import com.app.security.RateLimitProperties;
import com.app.security.RateLimitService;
import com.app.security.SecurityAuditLogger;
import com.app.security.SecurityConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.health.actuate.endpoint.AdditionalHealthEndpointPath;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroup;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.health.actuate.endpoint.HttpCodeStatusMapper;
import org.springframework.boot.health.actuate.endpoint.StatusAggregator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.registry.DefaultHealthContributorRegistry;
import org.springframework.boot.health.registry.DefaultReactiveHealthContributorRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobre o probe consumido pelo HEALTHCHECK do container e pelo smoke test do
 * CD (Epico 8), agora delegando ao {@code HealthEndpoint} para saber se o
 * banco esta acessivel (US-002, Epico 12). SecurityConfig entra no slice via
 * @Import - sem ela o teste de acesso anonimo nao provaria nada, porque o
 * slice usaria a cadeia de seguranca padrao do Spring Boot em vez da do app.
 *
 * <p>{@code HealthDescriptor} (o retorno de {@code HealthEndpoint.health()})
 * e uma classe sealed do Actuator - Mockito nao consegue mocka-la ("Sealed
 * interfaces or abstract classes can't be mocked"). Por isso este teste nao
 * mocka {@code HealthEndpoint}: monta um bean real, com um
 * {@code HealthIndicator} "db" fake registrado num {@code HealthContributorRegistry}
 * de verdade, e controla o {@code Health} que ele devolve por teste via
 * {@link #DB_HEALTH}.</p>
 */
@WebMvcTest(HealthController.class)
@Import({ SecurityConfig.class, ClientIpResolver.class, RateLimitService.class, RateLimitProperties.class,
	SecurityAuditLogger.class, HealthCheckExecutorConfig.class })
class HealthControllerTest {

	private static final AtomicReference<Supplier<Health>> DB_HEALTH = new AtomicReference<>(
			() -> Health.up().build());

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private JwtService jwtService;

	@TestConfiguration(proxyBeanMethods = false)
	static class FakeHealthEndpointConfig {

		@Bean
		HealthEndpoint healthEndpoint() {
			DefaultHealthContributorRegistry registry = new DefaultHealthContributorRegistry();
			HealthIndicator dbIndicator = () -> DB_HEALTH.get().get();
			registry.registerContributor("db", dbIndicator);

			HealthEndpointGroup primaryGroup = new HealthEndpointGroup() {

				@Override
				public boolean isMember(String contributorName) {
					return true;
				}

				@Override
				public boolean showComponents(SecurityContext securityContext) {
					return true;
				}

				@Override
				public boolean showDetails(SecurityContext securityContext) {
					return true;
				}

				@Override
				public StatusAggregator getStatusAggregator() {
					return StatusAggregator.getDefault();
				}

				@Override
				public HttpCodeStatusMapper getHttpCodeStatusMapper() {
					return HttpCodeStatusMapper.getDefault();
				}

				@Override
				public AdditionalHealthEndpointPath getAdditionalPath() {
					return null;
				}
			};

			HealthEndpointGroups groups = HealthEndpointGroups.of(primaryGroup, Map.of());
			return new HealthEndpoint(registry, new DefaultReactiveHealthContributorRegistry(), groups,
					Duration.ofSeconds(10));
		}
	}

	@Test
	void healthReturnsUpWhenDatabaseIsUp() throws Exception {
		DB_HEALTH.set(() -> Health.up().build());

		mockMvc.perform(get("/api/health"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(content().json("{\"status\":\"UP\",\"db\":\"UP\"}", true));
	}

	@Test
	void healthReturnsDownWhenDatabaseIsDown() throws Exception {
		DB_HEALTH.set(() -> Health.down(new RuntimeException("connection refused to jdbc:postgresql://db-host/sessao "
				+ "user=sessao password=secret")).build());

		String body = mockMvc.perform(get("/api/health"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(content().json("{\"status\":\"DOWN\",\"db\":\"DOWN\"}", true))
			.andReturn()
			.getResponse()
			.getContentAsString();

		// A resposta de falha nunca pode conter credencial, host/URL do
		// datasource, usuario ou stack trace - so o vocabulario fixo, mesmo
		// que o Health subjacente (acima) carregue esse detalhe.
		assertThat(body).doesNotContainIgnoringCase("jdbc")
			.doesNotContainIgnoringCase("postgres")
			.doesNotContainIgnoringCase("password")
			.doesNotContainIgnoringCase("secret")
			.doesNotContainIgnoringCase("exception")
			.doesNotContainIgnoringCase("db-host");
	}

	@Test
	void healthReturnsDownWhenDatabaseCheckTimesOut() throws Exception {
		DB_HEALTH.set(() -> {
			try {
				TimeUnit.SECONDS.sleep(5);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			return Health.up().build();
		});

		long start = System.nanoTime();

		String body = mockMvc.perform(get("/api/health"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(content().json("{\"status\":\"DOWN\",\"db\":\"DOWN\"}", true))
			.andReturn()
			.getResponse()
			.getContentAsString();

		long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

		// AC: no maximo ~2s, nao os 5s que a checagem pendurada levaria.
		assertThat(elapsedMillis).isLessThan(4000);
		assertThat(body).doesNotContainIgnoringCase("exception").doesNotContainIgnoringCase("timeout");
	}

	@Test
	void healthIsPublicAndNeverConsultsTheAccessToken() throws Exception {
		DB_HEALTH.set(() -> Health.up().build());

		mockMvc.perform(get("/api/health"))
			.andExpect(status().isOk());

		// Nem o Docker nem o runner do CD tem cookie de sessao: se um dia a rota
		// sair do permitAll, o JwtService passaria a ser consultado aqui.
		verifyNoInteractions(jwtService);
	}
}
