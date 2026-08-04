package com.app.auth;

import com.app.couple.CoupleService;
import com.app.security.JwtService;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({ SecurityConfig.class, AuthCookieService.class })
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
