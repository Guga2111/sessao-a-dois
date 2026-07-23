package com.app.match;

import com.app.couple.Couple;
import com.app.couple.CoupleService;
import com.app.media.MediaType;
import com.app.security.JwtService;
import com.app.security.SecurityConfig;
import com.app.tracking.ResourceNotFoundException;

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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MatchController.class)
@Import(SecurityConfig.class)
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
				.with(authentication(authenticatedUser(userId)))
				.contentType("application/json")
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.tmdbId").exists())
			.andExpect(jsonPath("$.errors.mediaType").exists());
	}
}
