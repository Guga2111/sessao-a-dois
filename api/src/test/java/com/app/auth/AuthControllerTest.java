package com.app.auth;

import com.app.couple.Couple;
import com.app.couple.CoupleResponseMapper;
import com.app.couple.CoupleService;
import com.app.security.ClientIpResolver;
import com.app.security.JwtService;
import com.app.security.RateLimitProperties;
import com.app.security.RateLimitService;
import com.app.security.SecurityAuditLogger;
import com.app.security.SecurityConfig;
import com.app.user.User;
import com.app.user.UserRepository;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({ SecurityConfig.class, AuthCookieService.class, ClientIpResolver.class, RateLimitService.class,
	RateLimitProperties.class, SecurityAuditLogger.class, CoupleResponseMapper.class })
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuthService authService;

	@MockitoBean
	private CoupleService coupleService;

	@MockitoBean
	private UserRepository userRepository;

	@MockitoBean
	private JwtService jwtService;

	@Test
	void registersSuccessfully() throws Exception {
		User user = new User("Ana", "ana@example.com", "hashed-password");
		when(authService.register(any(RegisterRequest.class))).thenReturn(user);

		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Ana","email":"ana@example.com","password":"senha1234"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("Ana"))
			.andExpect(jsonPath("$.email").value("ana@example.com"));
	}

	@Test
	void rejectsDuplicateEmailWithConflict() throws Exception {
		when(authService.register(any(RegisterRequest.class))).thenThrow(new EmailAlreadyExistsException());

		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Ana","email":"ana@example.com","password":"senha1234"}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("e-mail ja cadastrado"));
	}

	@Test
	void rejectsShortPasswordWithBadRequest() throws Exception {
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Ana","email":"ana@example.com","password":"123"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.password").exists());
	}

	@Test
	void rejectsPasswordOverSeventyTwoCharsWithBadRequest() throws Exception {
		String tooLongPassword = "a".repeat(73);

		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Ana","email":"ana@example.com","password":"%s"}
					""".formatted(tooLongPassword)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.password").exists());
	}

	@Test
	void rejectsInvalidEmailWithBadRequest() throws Exception {
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Ana","email":"not-an-email","password":"senha1234"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.email").exists());
	}

	@Test
	void logsInSuccessfullyAndSetsSessionCookies() throws Exception {
		User user = new User("Ana", "ana@example.com", "hashed-password");
		when(authService.login(any(LoginRequest.class), any(), any()))
			.thenReturn(new AuthService.LoginResult("access-token-value", "refresh-token-value", user));

		MvcResult result = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"ana@example.com","password":"senha1234"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.token").doesNotExist())
			.andExpect(jsonPath("$.user.email").value("ana@example.com"))
			.andExpect(jsonPath("$.couple").doesNotExist())
			.andReturn();

		List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
		assertThat(setCookies).hasSize(2);

		String accessCookie = setCookies.stream().filter(c -> c.startsWith("access_token=")).findFirst().orElseThrow();
		assertThat(accessCookie).contains("access_token=access-token-value")
			.contains("HttpOnly")
			.contains("SameSite=Strict")
			.contains("Path=/")
			.doesNotContain("Path=/api/auth/refresh");

		String refreshCookie = setCookies.stream().filter(c -> c.startsWith("refresh_token=")).findFirst().orElseThrow();
		assertThat(refreshCookie).contains("refresh_token=refresh-token-value")
			.contains("HttpOnly")
			.contains("SameSite=Strict")
			.contains("Path=/api/auth/refresh");
	}

	@Test
	void rejectsLoginWithBlankEmailAsBadRequest() throws Exception {
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"","password":"senha1234"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.email").exists());

		verify(authService, never()).login(any(), any(), any());
	}

	@Test
	void rejectsLoginWithMalformedEmailAsBadRequest() throws Exception {
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"not-an-email","password":"senha1234"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.email").exists());

		verify(authService, never()).login(any(), any(), any());
	}

	@Test
	void rejectsLoginWithBlankPasswordAsBadRequest() throws Exception {
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"ana@example.com","password":""}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.password").exists());

		verify(authService, never()).login(any(), any(), any());
	}

	@Test
	void rejectsLoginWithInvalidCredentials() throws Exception {
		when(authService.login(any(LoginRequest.class), any(), any())).thenThrow(new InvalidCredentialsException());

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"ana@example.com","password":"errada"}
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("credenciais invalidas"));
	}

	@Test
	void refreshRotatesSessionAndSetsBothCookies() throws Exception {
		when(authService.refresh("valid-refresh-token", null, "127.0.0.1"))
			.thenReturn(new AuthService.RefreshResult("new-access-token", "new-refresh-token"));

		MvcResult result = mockMvc.perform(post("/api/auth/refresh")
				.cookie(new jakarta.servlet.http.Cookie("refresh_token", "valid-refresh-token")))
			.andExpect(status().isNoContent())
			.andReturn();

		List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
		assertThat(setCookies).hasSize(2);

		String accessCookie = setCookies.stream().filter(c -> c.startsWith("access_token=")).findFirst().orElseThrow();
		assertThat(accessCookie).contains("access_token=new-access-token").contains("Path=/");

		String refreshCookie = setCookies.stream().filter(c -> c.startsWith("refresh_token=")).findFirst().orElseThrow();
		assertThat(refreshCookie).contains("refresh_token=new-refresh-token").contains("Path=/api/auth/refresh");
	}

	@Test
	void refreshWithoutCookieIsRejected() throws Exception {
		mockMvc.perform(post("/api/auth/refresh"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void refreshWithBlankCookieIsRejected() throws Exception {
		mockMvc.perform(post("/api/auth/refresh")
				.cookie(new jakarta.servlet.http.Cookie("refresh_token", "")))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void refreshPropagatesReuseDetectionAsUnauthorized() throws Exception {
		when(authService.refresh(any(), any(), any())).thenThrow(new RefreshReuseDetectedException());

		mockMvc.perform(post("/api/auth/refresh")
				.cookie(new jakarta.servlet.http.Cookie("refresh_token", "reused-token")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("reuso de refresh token detectado"));
	}

	@Test
	void logoutWithCookieRevokesAndExpiresBothCookies() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/logout")
				.cookie(new jakarta.servlet.http.Cookie("refresh_token", "refresh-token-value")))
			.andExpect(status().isNoContent())
			.andReturn();

		verify(authService).logout("refresh-token-value");

		List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
		assertThat(setCookies).hasSize(2);

		String accessCookie = setCookies.stream().filter(c -> c.startsWith("access_token=")).findFirst().orElseThrow();
		assertThat(accessCookie).contains("Max-Age=0").contains("Path=/").doesNotContain("Path=/api/auth/refresh");

		String refreshCookie = setCookies.stream().filter(c -> c.startsWith("refresh_token=")).findFirst().orElseThrow();
		assertThat(refreshCookie).contains("Max-Age=0").contains("Path=/api/auth/refresh");
	}

	@Test
	void logoutWithoutCookieIsIdempotent() throws Exception {
		mockMvc.perform(post("/api/auth/logout"))
			.andExpect(status().isNoContent());

		verify(authService, never()).logout(any());
	}

	@Test
	void deniesMeWithoutSession() throws Exception {
		mockMvc.perform(get("/api/auth/me"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void returnsCurrentSessionWithCouple() throws Exception {
		UUID userId = UUID.randomUUID();
		User user = new User("Ana", "ana@example.com", "hashed-password");
		ReflectionTestUtils.setField(user, "id", userId);
		when(authService.findAuthenticatedUser(userId)).thenReturn(user);

		Couple couple = new Couple(userId, "ABC234");
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.of(couple));

		mockMvc.perform(get("/api/auth/me")
				.with(authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.email").value("ana@example.com"))
			.andExpect(jsonPath("$.couple.inviteCode").value("ABC234"));
	}

	@Test
	void returnsCurrentSessionWithoutCoupleAsNull() throws Exception {
		UUID userId = UUID.randomUUID();
		User user = new User("Ana", "ana@example.com", "hashed-password");
		ReflectionTestUtils.setField(user, "id", userId);
		when(authService.findAuthenticatedUser(userId)).thenReturn(user);
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/auth/me")
				.with(authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.email").value("ana@example.com"))
			.andExpect(jsonPath("$.couple").value(org.hamcrest.Matchers.nullValue()));
	}

	@Nested
	@TestPropertySource(properties = "app.auth.cookie.secure=true")
	class SecureFlagEnabled {

		@Test
		void marksCookiesSecureWhenConfiguredTrue() throws Exception {
			User user = new User("Ana", "ana@example.com", "hashed-password");
			when(authService.login(any(LoginRequest.class), any(), any()))
				.thenReturn(new AuthService.LoginResult("access-token-value", "refresh-token-value", user));

			MvcResult result = mockMvc.perform(post("/api/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
						{"email":"ana@example.com","password":"senha1234"}
						"""))
				.andExpect(status().isOk())
				.andReturn();

			List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
			assertThat(setCookies).allMatch(c -> c.contains("Secure"));
		}
	}

	@Nested
	@TestPropertySource(properties = "app.auth.cookie.secure=false")
	class SecureFlagDisabled {

		@Test
		void omitsSecureFlagWhenConfiguredFalse() throws Exception {
			User user = new User("Ana", "ana@example.com", "hashed-password");
			when(authService.login(any(LoginRequest.class), any(), any()))
				.thenReturn(new AuthService.LoginResult("access-token-value", "refresh-token-value", user));

			MvcResult result = mockMvc.perform(post("/api/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
						{"email":"ana@example.com","password":"senha1234"}
						"""))
				.andExpect(status().isOk())
				.andReturn();

			List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
			assertThat(setCookies).noneMatch(c -> c.contains("Secure"));
		}
	}
}
