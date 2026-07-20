package com.app.security;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.jsonwebtoken.ExpiredJwtException;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobre a integracao do {@link JwtAuthenticationFilter} com as regras de
 * autorizacao de {@link SecurityConfig}, usando um controller de teste
 * dedicado (o dominio ainda nao tem nenhuma rota protegida real neste
 * epico).
 */
@WebMvcTest(controllers = JwtAuthenticationFilterTest.ProtectedTestController.class)
@Import(SecurityConfig.class)
class JwtAuthenticationFilterTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private JwtService jwtService;

	@Test
	void deniesAccessWithoutToken() throws Exception {
		mockMvc.perform(get("/api/test/protected"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void allowsAccessWithValidToken() throws Exception {
		UUID userId = UUID.randomUUID();
		when(jwtService.parseSubject("valid-token")).thenReturn(userId);

		mockMvc.perform(get("/api/test/protected").header("Authorization", "Bearer valid-token"))
			.andExpect(status().isOk());
	}

	@Test
	void deniesAccessWithExpiredToken() throws Exception {
		when(jwtService.parseSubject("expired-token"))
			.thenThrow(new ExpiredJwtException(null, null, "expired"));

		mockMvc.perform(get("/api/test/protected").header("Authorization", "Bearer expired-token"))
			.andExpect(status().isUnauthorized());
	}

	@RestController
	static class ProtectedTestController {

		@GetMapping("/api/test/protected")
		public String protectedEndpoint() {
			return "ok";
		}
	}
}
