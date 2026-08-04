package com.app.security;

import com.app.auth.AuthCookieService;
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

import jakarta.servlet.http.Cookie;

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
	void deniesAccessWithoutCookie() throws Exception {
		mockMvc.perform(get("/api/couple/me"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void allowsAccessWithValidCookie() throws Exception {
		UUID userId = UUID.randomUUID();
		when(jwtService.parseSubject("valid-token")).thenReturn(userId);
		when(coupleService.getCurrentCouple(any(UUID.class))).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/couple/me")
				.cookie(new Cookie(AuthCookieService.ACCESS_TOKEN_COOKIE, "valid-token")))
			.andExpect(status().isNotFound()); // 404 = security passed, no couple found
	}

	@Test
	void deniesAccessWithExpiredCookie() throws Exception {
		when(jwtService.parseSubject("expired-token"))
			.thenThrow(new ExpiredJwtException(null, null, "expired"));

		mockMvc.perform(get("/api/couple/me")
				.cookie(new Cookie(AuthCookieService.ACCESS_TOKEN_COOKIE, "expired-token")))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void deniesAccessWithBlankCookieWithoutError() throws Exception {
		mockMvc.perform(get("/api/couple/me")
				.cookie(new Cookie(AuthCookieService.ACCESS_TOKEN_COOKIE, "  ")))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void deniesAccessWithMalformedCookieWithoutError() throws Exception {
		when(jwtService.parseSubject("not-a-jwt"))
			.thenThrow(new IllegalArgumentException("malformed"));

		mockMvc.perform(get("/api/couple/me")
				.cookie(new Cookie(AuthCookieService.ACCESS_TOKEN_COOKIE, "not-a-jwt")))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void headerWithoutCookieDoesNotAuthenticate() throws Exception {
		mockMvc.perform(get("/api/couple/me").header("Authorization", "Bearer valid-token"))
			.andExpect(status().isUnauthorized());
	}
}
