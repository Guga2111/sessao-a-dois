package com.app.user;

import com.app.auth.EmailAlreadyExistsException;
import com.app.security.ClientIpResolver;
import com.app.security.JwtService;
import com.app.security.RateLimitExceededException;
import com.app.security.RateLimitProperties;
import com.app.security.RateLimitService;
import com.app.security.SecurityAuditLogger;
import com.app.security.SecurityConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserProfileController.class)
@Import({ SecurityConfig.class, ClientIpResolver.class, RateLimitService.class, RateLimitProperties.class,
	SecurityAuditLogger.class, UserExceptionHandler.class })
class UserProfileControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserProfileService userProfileService;

	@MockitoBean
	private JwtService jwtService;

	private RequestPostProcessor as(UUID userId) {
		return authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
	}

	@Test
	void deniesAccessWithoutAuthentication() throws Exception {
		mockMvc.perform(patch("/api/user/me")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Ana\"}"))
			.andExpect(status().isUnauthorized());

		verifyNoInteractions(userProfileService);
	}

	@Test
	void updatesTheProfileOfTheAuthenticatedUser() throws Exception {
		UUID userId = UUID.randomUUID();
		when(userProfileService.updateProfile(eq(userId), eq(new UpdateProfileRequest("Ana Paula", "nova@example.com"))))
			.thenReturn(new UserProfileResponse(userId, "Ana Paula", "nova@example.com"));

		mockMvc.perform(patch("/api/user/me")
				.with(csrf())
				.with(as(userId))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Ana Paula\",\"email\":\"nova@example.com\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(userId.toString()))
			.andExpect(jsonPath("$.name").value("Ana Paula"))
			.andExpect(jsonPath("$.email").value("nova@example.com"));
	}

	/** Semantica de PATCH: o campo ausente chega null no request, nao vazio. */
	@Test
	void acceptsAPayloadWithOnlyOneField() throws Exception {
		UUID userId = UUID.randomUUID();
		when(userProfileService.updateProfile(eq(userId), eq(new UpdateProfileRequest("Ana Paula", null))))
			.thenReturn(new UserProfileResponse(userId, "Ana Paula", "ana@example.com"));

		mockMvc.perform(patch("/api/user/me")
				.with(csrf())
				.with(as(userId))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Ana Paula\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value("ana@example.com"));
	}

	@Test
	void returnsConflictWhenTheEmailBelongsToAnotherAccount() throws Exception {
		UUID userId = UUID.randomUUID();
		when(userProfileService.updateProfile(eq(userId), eq(new UpdateProfileRequest(null, "ocupado@example.com"))))
			.thenThrow(new EmailAlreadyExistsException());

		mockMvc.perform(patch("/api/user/me")
				.with(csrf())
				.with(as(userId))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"ocupado@example.com\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").exists());
	}

	@Test
	void rejectsAMalformedEmail() throws Exception {
		UUID userId = UUID.randomUUID();

		mockMvc.perform(patch("/api/user/me")
				.with(csrf())
				.with(as(userId))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"nao-e-email\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("dados invalidos"))
			.andExpect(jsonPath("$.errors.email").value("e-mail invalido"));

		verifyNoInteractions(userProfileService);
	}

	@Test
	void rejectsABlankName() throws Exception {
		UUID userId = UUID.randomUUID();

		mockMvc.perform(patch("/api/user/me")
				.with(csrf())
				.with(as(userId))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"   \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.name").value("nome nao pode ser vazio"));

		verifyNoInteractions(userProfileService);
	}

	@Test
	void returnsTooManyRequestsWithRetryAfterWhenRateLimited() throws Exception {
		UUID userId = UUID.randomUUID();
		when(userProfileService.updateProfile(eq(userId), eq(new UpdateProfileRequest("Ana Paula", null))))
			.thenThrow(new RateLimitExceededException(42));

		mockMvc.perform(patch("/api/user/me")
				.with(csrf())
				.with(as(userId))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Ana Paula\"}"))
			.andExpect(status().isTooManyRequests())
			.andExpect(header().string("Retry-After", "42"));
	}
}
