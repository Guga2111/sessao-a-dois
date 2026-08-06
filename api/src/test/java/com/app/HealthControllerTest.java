package com.app;

import com.app.security.ClientIpResolver;
import com.app.security.JwtService;
import com.app.security.RateLimitProperties;
import com.app.security.RateLimitService;
import com.app.security.SecurityAuditLogger;
import com.app.security.SecurityConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobre o probe consumido pelo HEALTHCHECK do container e pelo smoke test do
 * CD (Epico 8). SecurityConfig entra no slice via @Import - sem ela o teste de
 * acesso anonimo nao provaria nada, porque o slice usaria a cadeia de
 * seguranca padrao do Spring Boot em vez da do app.
 */
@WebMvcTest(HealthController.class)
@Import({ SecurityConfig.class, ClientIpResolver.class, RateLimitService.class, RateLimitProperties.class,
	SecurityAuditLogger.class })
class HealthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private JwtService jwtService;

	@Test
	void healthReturnsUp() throws Exception {
		mockMvc.perform(get("/api/health"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(content().json("{\"status\":\"UP\"}", true))
			.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void healthIsPublicAndNeverConsultsTheAccessToken() throws Exception {
		mockMvc.perform(get("/api/health"))
			.andExpect(status().isOk());

		// Nem o Docker nem o runner do CD tem cookie de sessao: se um dia a rota
		// sair do permitAll, o JwtService passaria a ser consultado aqui.
		verifyNoInteractions(jwtService);
	}
}
