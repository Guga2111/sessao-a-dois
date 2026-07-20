package com.app.security;

import com.app.couple.CoupleController;
import com.app.couple.CoupleService;
import com.app.user.UserRepository;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.jsonwebtoken.ExpiredJwtException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobre a integracao do {@link JwtAuthenticationFilter} com as regras de
 * autorizacao de {@link SecurityConfig}, usando {@link CoupleController} como
 * endpoint protegido representativo.
 */
@WebMvcTest(CoupleController.class)
@Import(SecurityConfig.class)
class JwtAuthenticationFilterTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private JwtService jwtService;

	@MockitoBean
	private CoupleService coupleService;

	@MockitoBean
	private UserRepository userRepository;

	@Test
	void deniesAccessWithoutToken() throws Exception {
		mockMvc.perform(get("/api/couple/me"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void allowsAccessWithValidToken() throws Exception {
		UUID userId = UUID.randomUUID();
		when(jwtService.parseSubject("valid-token")).thenReturn(userId);
		when(coupleService.getCurrentCouple(any(UUID.class))).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/couple/me").header("Authorization", "Bearer valid-token"))
			.andExpect(status().isNotFound()); // 404 = security passed, no couple found
	}

	@Test
	void deniesAccessWithExpiredToken() throws Exception {
		when(jwtService.parseSubject("expired-token"))
			.thenThrow(new ExpiredJwtException(null, null, "expired"));

		mockMvc.perform(get("/api/couple/me").header("Authorization", "Bearer expired-token"))
			.andExpect(status().isUnauthorized());
	}
}
