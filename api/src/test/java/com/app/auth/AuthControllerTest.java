package com.app.auth;

import com.app.security.JwtService;
import com.app.security.SecurityConfig;
import com.app.user.User;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuthService authService;

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
	void logsInSuccessfully() throws Exception {
		User user = new User("Ana", "ana@example.com", "hashed-password");
		when(authService.login(any(LoginRequest.class)))
			.thenReturn(new AuthService.LoginResult("jwt-token", user));

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"ana@example.com","password":"senha1234"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.token").value("jwt-token"))
			.andExpect(jsonPath("$.user.email").value("ana@example.com"))
			.andExpect(jsonPath("$.couple").doesNotExist());
	}

	@Test
	void rejectsLoginWithInvalidCredentials() throws Exception {
		when(authService.login(any(LoginRequest.class))).thenThrow(new InvalidCredentialsException());

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"ana@example.com","password":"errada"}
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("credenciais invalidas"));
	}
}
