package com.app.match;

import com.app.couple.Couple;
import com.app.couple.CoupleService;
import com.app.media.MediaType;
import com.app.security.ClientIpResolver;
import com.app.security.JwtService;
import com.app.security.RateLimitProperties;
import com.app.security.RateLimitService;
import com.app.security.SecurityAuditLogger;
import com.app.security.SecurityConfig;
import com.app.common.ResourceNotFoundException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MatchController.class)
@Import({ SecurityConfig.class, ClientIpResolver.class, RateLimitService.class, RateLimitProperties.class, SecurityAuditLogger.class })
class MatchControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MatchService matchService;

	@MockitoBean
	private CoupleService coupleService;

	@MockitoBean
	private JwtService jwtService;

	private static UsernamePasswordAuthenticationToken authenticatedUser(UUID userId) {
		return new UsernamePasswordAuthenticationToken(userId, null, List.of());
	}

	private Couple couple(UUID coupleId, UUID userId) {
		Couple couple = new Couple(userId, "ABC234");
		ReflectionTestUtils.setField(couple, "id", coupleId);
		return couple;
	}

	@Test
	void like_deniesAccessWithoutAuthentication() throws Exception {
		mockMvc.perform(post("/api/match/like")
				.with(csrf())
				.contentType("application/json")
				.content("{\"tmdbId\":603,\"mediaType\":\"MOVIE\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void like_returnsMatchedFlagFromService() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.of(couple(coupleId, userId)));
		when(matchService.like(eq(coupleId), eq(userId), any(LikeRequest.class)))
			.thenReturn(new LikeResponse(true));

		mockMvc.perform(post("/api/match/like")
				.with(csrf())
				.with(authentication(authenticatedUser(userId)))
				.contentType("application/json")
				.content("{\"tmdbId\":603,\"mediaType\":\"MOVIE\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.matched").value(true));
	}

	@Test
	void like_returnsConflictWhenTitleAlreadyTracked() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.of(couple(coupleId, userId)));
		when(matchService.like(eq(coupleId), eq(userId), any(LikeRequest.class)))
			.thenThrow(new TitleAlreadyTrackedException());

		mockMvc.perform(post("/api/match/like")
				.with(csrf())
				.with(authentication(authenticatedUser(userId)))
				.contentType("application/json")
				.content("{\"tmdbId\":603,\"mediaType\":\"MOVIE\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("titulo ja esta em uma lista do casal"));
	}

	@Test
	void like_returnsNotFoundWhenUserHasNoCouple() throws Exception {
		UUID userId = UUID.randomUUID();
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.empty());

		mockMvc.perform(post("/api/match/like")
				.with(csrf())
				.with(authentication(authenticatedUser(userId)))
				.contentType("application/json")
				.content("{\"tmdbId\":603,\"mediaType\":\"MOVIE\"}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("usuario nao pertence a nenhum casal"));
	}

	@Test
	void like_rejectsMissingFieldsWithBadRequest() throws Exception {
		UUID userId = UUID.randomUUID();

		mockMvc.perform(post("/api/match/like")
				.with(csrf())
				.with(authentication(authenticatedUser(userId)))
				.contentType("application/json")
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.tmdbId").exists())
			.andExpect(jsonPath("$.errors.mediaType").exists());
	}

	@Test
	void reject_deniesAccessWithoutAuthentication() throws Exception {
		mockMvc.perform(post("/api/match/reject")
				.with(csrf())
				.contentType("application/json")
				.content("{\"tmdbId\":603,\"mediaType\":\"MOVIE\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void reject_returnsOkOnSuccess() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.of(couple(coupleId, userId)));
		doNothing().when(matchService).reject(eq(coupleId), eq(userId), any(LikeRequest.class));

		mockMvc.perform(post("/api/match/reject")
				.with(csrf())
				.with(authentication(authenticatedUser(userId)))
				.contentType("application/json")
				.content("{\"tmdbId\":603,\"mediaType\":\"MOVIE\"}"))
			.andExpect(status().isOk());
	}

	@Test
	void reject_rejectsMissingFieldsWithBadRequest() throws Exception {
		UUID userId = UUID.randomUUID();

		mockMvc.perform(post("/api/match/reject")
				.with(csrf())
				.with(authentication(authenticatedUser(userId)))
				.contentType("application/json")
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.tmdbId").exists())
			.andExpect(jsonPath("$.errors.mediaType").exists());
	}

	@Test
	void pending_deniesAccessWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/match/pending"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void pending_returnsList() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.of(couple(coupleId, userId)));
		when(matchService.getPending(coupleId, userId))
			.thenReturn(List.of(new PendingMatchDto(603L, MediaType.MOVIE, "Matrix", "/poster.jpg", 1999)));

		mockMvc.perform(get("/api/match/pending")
				.with(authentication(authenticatedUser(userId))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].tmdbId").value(603))
			.andExpect(jsonPath("$[0].title").value("Matrix"))
			.andExpect(jsonPath("$[0].mediaType").value("MOVIE"));
	}

	@Test
	void pending_returnsNotFoundWhenUserHasNoCouple() throws Exception {
		UUID userId = UUID.randomUUID();
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/match/pending")
				.with(authentication(authenticatedUser(userId))))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("usuario nao pertence a nenhum casal"));
	}
}
