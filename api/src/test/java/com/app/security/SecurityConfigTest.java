package com.app.security;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fixa contrato de quais rotas sao publicas e quais exigem autenticacao
 * (SecurityConfig#securityFilterChain), para que um permitAll() acidental
 * numa rota privada quebre o CI em vez de ser descoberto em producao.
 *
 * <p><b>O {@code @DirtiesContext(BEFORE_CLASS)} nao e cosmetico e nao pode ser
 * removido.</b> Esta classe e a unica que exercita o {@code CookieCsrfTokenRepository}
 * de verdade (o cookie {@code XSRF-TOKEN} escrito na resposta), e o helper
 * {@code SecurityMockMvcRequestPostProcessors.csrf()} do spring-security-test
 * TROCA PERMANENTEMENTE o {@code tokenRepository} do {@code CsrfFilter} da
 * aplicacao por um {@code TestCsrfTokenRepository} (que guarda o token num
 * request attribute e NUNCA escreve cookie) — e nao restaura o original no fim
 * do teste. Como quatro classes {@code @SpringBootTest @AutoConfigureMockMvc}
 * usam {@code csrf()} e compartilham exatamente este mesmo contexto em cache
 * ({@code UserProfileIntegrationTest}, {@code PasswordChangeIntegrationTest},
 * {@code UserDeletionIntegrationTest}, {@code CoupleDissolutionIntegrationTest}),
 * rodar depois de qualquer uma delas fazia
 * {@code healthResponseSetsTheCsrfCookieForTheSpaToRead} e
 * {@code authenticatedMutatingRequestWithValidCsrfTokenSucceeds} falharem — sem
 * nenhuma mudanca de codigo de producao, so por causa da ordem das classes, que
 * o Surefire deriva do filesystem e portanto muda entre a maquina local e o
 * runner do CI. Forcar um contexto limpo antes desta classe e o que torna o
 * resultado independente da ordem.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class SecurityConfigTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void passwordEncoderHashesWithStrength12ButStillValidatesStrength10Hashes() {
		String strength10Hash = new BCryptPasswordEncoder(10).encode("senha1234");

		assertThat(passwordEncoder.matches("senha1234", strength10Hash)).isTrue();
		assertThat(passwordEncoder.encode("senha1234")).startsWith("$2a$12$");
	}

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
	void refreshIsPublicAndRotatesTheSession() throws Exception {
		String email = uniqueEmail();
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerBody(email)))
			.andExpect(status().isCreated());

		MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"%s","password":"senha1234"}
					""".formatted(email)))
			.andExpect(status().isOk())
			.andReturn();

		String refreshTokenValue = cookieValue(loginResult, "refresh_token");

		mockMvc.perform(post("/api/auth/refresh")
				.cookie(new Cookie("refresh_token", refreshTokenValue)))
			.andExpect(status().isNoContent());
	}

	@Test
	void refreshWithoutCookieIsUnauthorizedNotForbidden() throws Exception {
		mockMvc.perform(post("/api/auth/refresh"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void logoutIsPublicAndRevokesTheRefreshTokenImmediately() throws Exception {
		String email = uniqueEmail();
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerBody(email)))
			.andExpect(status().isCreated());

		MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"%s","password":"senha1234"}
					""".formatted(email)))
			.andExpect(status().isOk())
			.andReturn();

		String refreshTokenValue = cookieValue(loginResult, "refresh_token");

		mockMvc.perform(post("/api/auth/logout")
				.cookie(new Cookie("refresh_token", refreshTokenValue)))
			.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/auth/refresh")
				.cookie(new Cookie("refresh_token", refreshTokenValue)))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void logoutWithoutCookieIsNoContentNotForbidden() throws Exception {
		mockMvc.perform(post("/api/auth/logout"))
			.andExpect(status().isNoContent());
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

	@Test
	void authenticatedMutatingRequestWithoutCsrfTokenIsForbidden() throws Exception {
		String accessTokenValue = registerLoginAndGetAccessToken();

		mockMvc.perform(post("/api/couple")
				.cookie(new Cookie("access_token", accessTokenValue)))
			.andExpect(status().isForbidden());
	}

	@Test
	void authenticatedMutatingRequestWithValidCsrfTokenSucceeds() throws Exception {
		String csrfTokenValue = fetchCsrfTokenCookieValue();
		String accessTokenValue = registerLoginAndGetAccessToken();

		mockMvc.perform(post("/api/couple")
				.cookie(new Cookie("access_token", accessTokenValue))
				.cookie(new Cookie("XSRF-TOKEN", csrfTokenValue))
				.header("X-XSRF-TOKEN", csrfTokenValue))
			.andExpect(status().isCreated());
	}

	@Test
	void getRequestNeverRequiresCsrfTokenEvenWhenAuthenticated() throws Exception {
		String accessTokenValue = registerLoginAndGetAccessToken();

		mockMvc.perform(get("/api/couple/me")
				.cookie(new Cookie("access_token", accessTokenValue)))
			.andExpect(status().isNotFound());
	}

	@Test
	void healthResponseSetsTheCsrfCookieForTheSpaToRead() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/health")).andReturn();

		assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
			.anyMatch(c -> c.startsWith("XSRF-TOKEN="));
	}

	private String fetchCsrfTokenCookieValue() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/health")).andReturn();
		return cookieValue(result, "XSRF-TOKEN");
	}

	private String registerLoginAndGetAccessToken() throws Exception {
		String email = uniqueEmail();
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerBody(email)))
			.andExpect(status().isCreated());

		MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"%s","password":"senha1234"}
					""".formatted(email)))
			.andExpect(status().isOk())
			.andReturn();

		return cookieValue(loginResult, "access_token");
	}

	private static String cookieValue(MvcResult result, String cookieName) {
		List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
		String prefix = cookieName + "=";
		return setCookies.stream()
			.filter(c -> c.startsWith(prefix))
			.map(c -> c.substring(prefix.length(), c.indexOf(';')))
			.findFirst()
			.orElseThrow();
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
