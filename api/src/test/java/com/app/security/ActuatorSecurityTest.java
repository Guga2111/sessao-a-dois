package com.app.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-001 (Epico 12): o Actuator entra no classpath so para os health
 * indicators (US-002 usa o {@code HealthEndpoint} como bean por baixo de
 * {@code HealthController}) - nenhum endpoint sob {@code /actuator/**} pode
 * responder 200 para requisicao anonima.
 *
 * <p>O status esperado aqui e 401, nao 404: {@code SecurityConfig} aplica
 * {@code anyRequest().authenticated()} no filtro do Spring Security, que roda
 * ANTES do DispatcherServlet tentar casar a rota - entao mesmo com
 * {@code management.endpoints.web.exposure.include} vazio (nenhum handler
 * mapeado para {@code /actuator/**}), a requisicao anonima e barrada pela
 * autenticacao antes de chegar a resolver que a rota nao existe.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class ActuatorSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void actuatorHealthDoesNotRespondOkToAnonymousRequest() throws Exception {
		mockMvc.perform(get("/actuator/health"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void actuatorRootDoesNotRespondOkToAnonymousRequest() throws Exception {
		mockMvc.perform(get("/actuator"))
			.andExpect(status().isUnauthorized());
	}
}
