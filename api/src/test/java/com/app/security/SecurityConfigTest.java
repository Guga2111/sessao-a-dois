package com.app.security;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fixa contrato de quais rotas sao publicas e quais exigem autenticacao
 * (SecurityConfig#securityFilterChain), para que um permitAll() acidental
 * numa rota privada quebre o CI em vez de ser descoberto em producao.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void healthRespondsOkWithoutAuthorizationHeader() throws Exception {
		mockMvc.perform(get("/api/health"))
			.andExpect(status().isOk());
	}

	@Test
	void registerIsPublic() throws Exception {
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerBody(uniqueEmail())))
			.andExpect(status().isCreated());
	}

	@Test
	void loginIsPublic() throws Exception {
		String email = uniqueEmail();
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerBody(email)))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"%s","password":"senha1234"}
					""".formatted(email)))
			.andExpect(status().isOk());
	}

	@Test
	void trackingRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/tracking/stats"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void matchPendingRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/match/pending"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void mediaSearchRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/media/search").param("query", "matrix"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void coupleMeRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/couple/me"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void notificationsRequireAuthentication() throws Exception {
		mockMvc.perform(get("/api/notifications"))
			.andExpect(status().isUnauthorized());
	}

	private static String uniqueEmail() {
		return "security-config-test-" + UUID.randomUUID() + "@example.com";
	}

	private static String registerBody(String email) {
		return """
			{"name":"Security Config Test","email":"%s","password":"senha1234"}
			""".formatted(email);
	}
}
