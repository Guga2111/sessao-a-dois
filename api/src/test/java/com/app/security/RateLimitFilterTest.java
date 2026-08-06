package com.app.security;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobre {@link RateLimitFilter}: bloqueio por IP em cada um dos 4 endpoints
 * protegidos, presenca/plausibilidade do Retry-After, liberacao apos a
 * janela, contadores independentes por IP, e ausencia de efeito em outros
 * endpoints. Capacidades/janelas sao reduzidas via @TestPropertySource so
 * that o teste rode em milissegundos em vez de esperar a janela default de
 * 1 minuto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
	"app.rate-limit.enabled=true",
	"app.rate-limit.login.capacity=2",
	"app.rate-limit.login.window=3s",
	"app.rate-limit.register.capacity=2",
	"app.rate-limit.register.window=3s",
	"app.rate-limit.refresh.capacity=2",
	"app.rate-limit.refresh.window=3s",
	"app.rate-limit.couple-join.capacity=2",
	"app.rate-limit.couple-join.window=3s"
})
class RateLimitFilterTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void loginBlocksAfterExceedingLimitWithRetryAfterHeader() throws Exception {
		String ip = uniqueIp();

		for (int i = 0; i < 2; i++) {
			mockMvc.perform(loginRequest(ip))
				.andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(429));
		}

		mockMvc.perform(loginRequest(ip))
			.andExpect(status().isTooManyRequests())
			.andExpect(header().exists("Retry-After"));
	}

	@Test
	void retryAfterHeaderIsAPlausiblePositiveNumber() throws Exception {
		String ip = uniqueIp();

		for (int i = 0; i < 2; i++) {
			mockMvc.perform(loginRequest(ip));
		}

		MvcResult result = mockMvc.perform(loginRequest(ip))
			.andExpect(status().isTooManyRequests())
			.andReturn();

		long retryAfter = Long.parseLong(result.getResponse().getHeader("Retry-After"));
		assertThat(retryAfter).isGreaterThan(0).isLessThanOrEqualTo(3);
	}

	@Test
	void registerBlocksAfterExceedingLimit() throws Exception {
		String ip = uniqueIp();

		for (int i = 0; i < 2; i++) {
			mockMvc.perform(registerRequest(ip))
				.andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(429));
		}

		mockMvc.perform(registerRequest(ip))
			.andExpect(status().isTooManyRequests())
			.andExpect(header().exists("Retry-After"));
	}

	@Test
	void refreshBlocksAfterExceedingLimit() throws Exception {
		String ip = uniqueIp();

		for (int i = 0; i < 2; i++) {
			mockMvc.perform(refreshRequest(ip))
				.andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(429));
		}

		mockMvc.perform(refreshRequest(ip))
			.andExpect(status().isTooManyRequests())
			.andExpect(header().exists("Retry-After"));
	}

	@Test
	void coupleJoinBlocksAfterExceedingLimit() throws Exception {
		String ip = uniqueIp();

		for (int i = 0; i < 2; i++) {
			mockMvc.perform(coupleJoinRequest(ip))
				.andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(429));
		}

		mockMvc.perform(coupleJoinRequest(ip))
			.andExpect(status().isTooManyRequests())
			.andExpect(header().exists("Retry-After"));
	}

	@Test
	void allowsAgainAfterWindowElapses() throws Exception {
		String ip = uniqueIp();

		for (int i = 0; i < 2; i++) {
			mockMvc.perform(loginRequest(ip));
		}
		mockMvc.perform(loginRequest(ip)).andExpect(status().isTooManyRequests());

		Thread.sleep(3200);

		mockMvc.perform(loginRequest(ip))
			.andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(429));
	}

	@Test
	void differentIpsHaveIndependentCounters() throws Exception {
		String ipA = uniqueIp();
		String ipB = uniqueIp();

		for (int i = 0; i < 2; i++) {
			mockMvc.perform(loginRequest(ipA))
				.andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(429));
		}
		mockMvc.perform(loginRequest(ipA)).andExpect(status().isTooManyRequests());

		mockMvc.perform(loginRequest(ipB))
			.andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(429));
	}

	@Test
	void otherEndpointsAreNeverRateLimited() throws Exception {
		String ip = uniqueIp();

		for (int i = 0; i < 10; i++) {
			mockMvc.perform(get("/api/tracking").header("X-Forwarded-For", ip))
				.andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(429));
			mockMvc.perform(get("/api/auth/me").header("X-Forwarded-For", ip))
				.andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(429));
		}
	}

	private static MockHttpServletRequestBuilder loginRequest(String ip) {
		return post("/api/auth/login")
			.header("X-Forwarded-For", ip)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"email":"nao-existe-%s@example.com","password":"senha1234"}
				""".formatted(UUID.randomUUID()));
	}

	private static MockHttpServletRequestBuilder registerRequest(String ip) {
		return post("/api/auth/register")
			.header("X-Forwarded-For", ip)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"name":"Rate Limit Test","email":"rate-limit-%s@example.com","password":"senha1234"}
				""".formatted(UUID.randomUUID()));
	}

	private static MockHttpServletRequestBuilder refreshRequest(String ip) {
		return post("/api/auth/refresh")
			.header("X-Forwarded-For", ip);
	}

	private static MockHttpServletRequestBuilder coupleJoinRequest(String ip) {
		return post("/api/couple/join")
			.header("X-Forwarded-For", ip)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"inviteCode":"ABCDEFGH"}
				""");
	}

	private static String uniqueIp() {
		return "10.0.%d.%d".formatted((int) (Math.random() * 255), (int) (Math.random() * 255));
	}
}
