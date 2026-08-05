package com.app.common;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.app.security.ClientIpResolver;
import com.app.security.JwtService;
import com.app.security.RateLimitProperties;
import com.app.security.RateLimitService;
import com.app.security.SecurityAuditLogger;
import com.app.security.SecurityConfig;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobre os 5 tipos de excecao tratados por {@link GlobalExceptionHandler}, atraves de
 * {@link GlobalExceptionHandlerTestSupportController} (so existe em src/test).
 */
@WebMvcTest(GlobalExceptionHandlerTestSupportController.class)
@Import({ GlobalExceptionHandler.class, SecurityConfig.class, ClientIpResolver.class, RateLimitService.class,
	RateLimitProperties.class, SecurityAuditLogger.class })
class GlobalExceptionHandlerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private JwtService jwtService;

	private static UsernamePasswordAuthenticationToken authenticatedUser() {
		return new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, List.of());
	}

	@Test
	void resourceNotFound_returns404WithMessage() throws Exception {
		mockMvc.perform(get("/test-global-exceptions/not-found").with(authentication(authenticatedUser())))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("recurso nao encontrado"));
	}

	@Test
	void accessDenied_returns403WithMessage() throws Exception {
		mockMvc.perform(get("/test-global-exceptions/access-denied").with(authentication(authenticatedUser())))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("acesso negado"));
	}

	@Test
	void rateLimitExceeded_returns429WithRetryAfterHeader() throws Exception {
		mockMvc.perform(get("/test-global-exceptions/rate-limited").with(authentication(authenticatedUser())))
			.andExpect(status().isTooManyRequests())
			.andExpect(header().string("Retry-After", "30"))
			.andExpect(jsonPath("$.message").exists());
	}

	@Test
	void methodArgumentNotValid_returns400WithFieldErrors() throws Exception {
		mockMvc.perform(post("/test-global-exceptions/validate")
				.with(csrf())
				.with(authentication(authenticatedUser()))
				.contentType("application/json")
				.content("{\"name\": \"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("dados invalidos"))
			.andExpect(jsonPath("$.errors.name").value("nao pode ser vazio"));
	}

	@Test
	void unhandledException_returns500WithGenericMessageOnly() throws Exception {
		mockMvc.perform(get("/test-global-exceptions/boom").with(authentication(authenticatedUser())))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.message").value("erro interno"))
			.andExpect(jsonPath("$.message").value(not(containsString("detalhe interno sensivel"))))
			.andExpect(content().string(not(containsString("detalhe interno sensivel"))))
			.andExpect(content().string(not(containsString("RuntimeException"))));
	}
}
