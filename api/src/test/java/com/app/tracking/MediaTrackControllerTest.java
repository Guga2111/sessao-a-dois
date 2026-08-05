package com.app.tracking;

import com.app.couple.Couple;
import com.app.couple.CoupleService;
import com.app.media.MediaType;
import com.app.security.ClientIpResolver;
import com.app.security.JwtService;
import com.app.security.RateLimitProperties;
import com.app.security.RateLimitService;
import com.app.security.SecurityAuditLogger;
import com.app.security.SecurityConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MediaTrackController.class)
@Import({ SecurityConfig.class, ClientIpResolver.class, RateLimitService.class, RateLimitProperties.class, SecurityAuditLogger.class })
class MediaTrackControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MediaTrackService mediaTrackService;

	@MockitoBean
	private UserReviewService userReviewService;

	@MockitoBean
	private CoupleService coupleService;

	@MockitoBean
	private StatsService statsService;

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
	void listKeys_returnsCoupleTrackKeys() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.of(couple(coupleId, userId)));
		when(mediaTrackService.listKeys(coupleId))
			.thenReturn(List.of(new TrackKeyResponse(MediaType.MOVIE, 603L)));

		mockMvc.perform(get("/api/tracking/keys")
				.with(authentication(authenticatedUser(userId))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].tmdbId").value(603))
			.andExpect(jsonPath("$[0].mediaType").value("MOVIE"));
	}

	@Test
	void listKeys_returnsNotFoundWhenUserHasNoCouple() throws Exception {
		UUID userId = UUID.randomUUID();
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/tracking/keys")
				.with(authentication(authenticatedUser(userId))))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("usuario nao pertence a nenhum casal"));
	}

	@Test
	void listKeys_deniesAccessWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/tracking/keys"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void listByStatus_returnsPagedResultWithDefaultPageAndSize() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.of(couple(coupleId, userId)));
		MediaTrackResponse track = new MediaTrackResponse(
			UUID.randomUUID(), 603L, MediaType.MOVIE, MediaStatus.WATCHING, null, 136, null, List.of(), "Matrix", "/poster.jpg", 1999);
		Page<MediaTrackResponse> page = new PageImpl<>(List.of(track), PageRequest.of(0, 20), 1);
		when(mediaTrackService.listByStatusPaged(coupleId, MediaStatus.WATCHING, 0, 20)).thenReturn(page);

		mockMvc.perform(get("/api/tracking").param("status", "WATCHING")
				.with(authentication(authenticatedUser(userId))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].tmdbId").value(603))
			.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void listByStatus_forwardsCustomPageAndSize() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.of(couple(coupleId, userId)));
		Page<MediaTrackResponse> page = new PageImpl<>(List.of(), PageRequest.of(2, 10), 25);
		when(mediaTrackService.listByStatusPaged(coupleId, MediaStatus.WATCHED, 2, 10)).thenReturn(page);

		mockMvc.perform(get("/api/tracking")
					.param("status", "WATCHED").param("page", "2").param("size", "10")
					.with(authentication(authenticatedUser(userId))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(25))
			.andExpect(jsonPath("$.number").value(2));
	}

	@Test
	void listByStatus_deniesAccessWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/tracking").param("status", "WATCHING"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void create_deniesAccessWithoutAuthentication() throws Exception {
		mockMvc.perform(post("/api/tracking")
				.with(csrf())
				.contentType("application/json")
				.content("{\"tmdbId\":603,\"mediaType\":\"MOVIE\",\"status\":\"WATCHING\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void create_createsTrackAndReturnsCreated() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.of(couple(coupleId, userId)));
		MediaTrackResponse response = new MediaTrackResponse(
			UUID.randomUUID(), 603L, MediaType.MOVIE, MediaStatus.WATCHING, null, 136, null, List.of(), "Matrix", "/poster.jpg", 1999);
		when(mediaTrackService.addTrack(eq(coupleId), eq(userId), any(CreateMediaTrackRequest.class)))
			.thenReturn(response);

		mockMvc.perform(post("/api/tracking")
				.with(csrf())
				.with(authentication(authenticatedUser(userId)))
				.contentType("application/json")
				.content("{\"tmdbId\":603,\"mediaType\":\"MOVIE\",\"status\":\"WATCHING\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.tmdbId").value(603));
	}

	@Test
	void delete_ofOwnTrackReturnsNoContent() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		UUID trackId = UUID.randomUUID();
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.of(couple(coupleId, userId)));

		mockMvc.perform(delete("/api/tracking/" + trackId)
				.with(csrf())
				.with(authentication(authenticatedUser(userId))))
			.andExpect(status().isNoContent());

		org.mockito.Mockito.verify(mediaTrackService).deleteTrack(trackId, coupleId);
	}

	@Test
	void delete_ofTrackFromAnotherCoupleReturnsNotFound() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		UUID trackId = UUID.randomUUID();
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.of(couple(coupleId, userId)));
		doThrow(new ResourceNotFoundException("titulo nao encontrado"))
			.when(mediaTrackService).deleteTrack(trackId, coupleId);

		mockMvc.perform(delete("/api/tracking/" + trackId)
				.with(csrf())
				.with(authentication(authenticatedUser(userId))))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("titulo nao encontrado"));
	}

	@Test
	void delete_deniesAccessWithoutAuthentication() throws Exception {
		mockMvc.perform(delete("/api/tracking/" + UUID.randomUUID()).with(csrf()))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void updateStatus_movesTrackToWatchingForAuthenticatedUser() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		UUID trackId = UUID.randomUUID();
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.of(couple(coupleId, userId)));
		MediaTrackResponse response = new MediaTrackResponse(
			trackId, 603L, MediaType.MOVIE, MediaStatus.WATCHING, null, 136, null, List.of(), "Matrix", "/poster.jpg", 1999);
		when(mediaTrackService.startWatching(trackId, coupleId, MediaStatus.WATCHING)).thenReturn(response);

		mockMvc.perform(patch("/api/tracking/" + trackId + "/status")
				.with(csrf())
				.with(authentication(authenticatedUser(userId)))
				.contentType("application/json")
				.content("{\"status\":\"WATCHING\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("WATCHING"));
	}

	@Test
	void updateStatus_ofTrackFromAnotherCoupleReturnsNotFound() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		UUID trackId = UUID.randomUUID();
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.of(couple(coupleId, userId)));
		when(mediaTrackService.startWatching(trackId, coupleId, MediaStatus.WATCHING))
			.thenThrow(new ResourceNotFoundException("titulo nao encontrado"));

		mockMvc.perform(patch("/api/tracking/" + trackId + "/status")
				.with(csrf())
				.with(authentication(authenticatedUser(userId)))
				.contentType("application/json")
				.content("{\"status\":\"WATCHING\"}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("titulo nao encontrado"));
	}

	@Test
	void updateStatus_rejectsInvalidTransitionWithBadRequest() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		UUID trackId = UUID.randomUUID();
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.of(couple(coupleId, userId)));
		when(mediaTrackService.startWatching(trackId, coupleId, MediaStatus.WATCHING))
			.thenThrow(new IllegalArgumentException("transicao de status invalida"));

		mockMvc.perform(patch("/api/tracking/" + trackId + "/status")
				.with(csrf())
				.with(authentication(authenticatedUser(userId)))
				.contentType("application/json")
				.content("{\"status\":\"WATCHING\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("transicao de status invalida"));
	}

	@Test
	void updateStatus_rejectsMissingStatusWithBadRequest() throws Exception {
		UUID userId = UUID.randomUUID();

		mockMvc.perform(patch("/api/tracking/" + UUID.randomUUID() + "/status")
				.with(csrf())
				.with(authentication(authenticatedUser(userId)))
				.contentType("application/json")
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.status").exists());
	}

	@Test
	void updateStatus_deniesAccessWithoutAuthentication() throws Exception {
		mockMvc.perform(patch("/api/tracking/" + UUID.randomUUID() + "/status")
				.with(csrf())
				.contentType("application/json")
				.content("{\"status\":\"WATCHING\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void stats_deniesAccessWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/tracking/stats"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void stats_returnsStatsForAuthenticatedUserCouple() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.of(couple(coupleId, userId)));
		StatsResponse response = new StatsResponse(
			10, 30, 2, 5, 3, 62.5, 37.5, 8, 4.5, "Acao",
			List.of(new GenreStat("Acao", 5, 100.0)),
			List.of(new MonthlyStat(1, 2L)));
		when(statsService.getStats(coupleId)).thenReturn(response);

		mockMvc.perform(get("/api/tracking/stats")
				.with(authentication(authenticatedUser(userId))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalWatchedHours").value(10))
			.andExpect(jsonPath("$.favoriteGenre").value("Acao"));
	}

	@Test
	void stats_returnsEmptyStateWhenUserHasNoCouple() throws Exception {
		UUID userId = UUID.randomUUID();
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.empty());
		StatsResponse empty = new StatsResponse(0, 0, 0, 0, 0, 0.0, 0.0, 0, 0.0, null, List.of(), List.of());
		when(statsService.emptyStats()).thenReturn(empty);

		mockMvc.perform(get("/api/tracking/stats")
				.with(authentication(authenticatedUser(userId))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalTitles").value(0))
			.andExpect(jsonPath("$.favoriteGenre").value(org.hamcrest.Matchers.nullValue()));
	}

	@Test
	void create_rejectsMissingRequiredFieldsWithBadRequest() throws Exception {
		UUID userId = UUID.randomUUID();

		mockMvc.perform(post("/api/tracking")
				.with(csrf())
				.with(authentication(authenticatedUser(userId)))
				.contentType("application/json")
				.content("{\"status\":\"WATCHING\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.tmdbId").exists())
			.andExpect(jsonPath("$.errors.mediaType").exists());
	}

	@Test
	void create_rejectsRatingWithoutWatchedStatusWithBadRequest() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.of(couple(coupleId, userId)));
		when(mediaTrackService.addTrack(eq(coupleId), eq(userId), any(CreateMediaTrackRequest.class)))
			.thenThrow(new IllegalArgumentException("rating e opinion so podem ser enviados com status WATCHED"));

		mockMvc.perform(post("/api/tracking")
				.with(csrf())
				.with(authentication(authenticatedUser(userId)))
				.contentType("application/json")
				.content("{\"tmdbId\":603,\"mediaType\":\"MOVIE\",\"status\":\"WATCHING\",\"rating\":5}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("rating e opinion so podem ser enviados com status WATCHED"));
	}

	@Test
	void markAsWatched_updatesTrackForAuthenticatedUser() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		UUID trackId = UUID.randomUUID();
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.of(couple(coupleId, userId)));
		MediaTrackResponse response = new MediaTrackResponse(
			trackId, 603L, MediaType.MOVIE, MediaStatus.WATCHED, null, 136, null, List.of(), "Matrix", "/poster.jpg", 1999);
		when(mediaTrackService.markAsWatched(eq(trackId), eq(coupleId), eq(userId), any(WatchRequest.class)))
			.thenReturn(response);

		mockMvc.perform(patch("/api/tracking/" + trackId + "/watch")
				.with(csrf())
				.with(authentication(authenticatedUser(userId)))
				.contentType("application/json")
				.content("{\"rating\":5,\"opinion\":\"otimo\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("WATCHED"));
	}

	@Test
	void markAsWatched_deniesAccessWithoutAuthentication() throws Exception {
		mockMvc.perform(patch("/api/tracking/" + UUID.randomUUID() + "/watch")
				.with(csrf())
				.contentType("application/json")
				.content("{\"rating\":5}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void markAsWatched_returnsNotFoundForUnknownTrack() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		UUID trackId = UUID.randomUUID();
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.of(couple(coupleId, userId)));
		when(mediaTrackService.markAsWatched(eq(trackId), eq(coupleId), eq(userId), any(WatchRequest.class)))
			.thenThrow(new ResourceNotFoundException("titulo nao encontrado"));

		mockMvc.perform(patch("/api/tracking/" + trackId + "/watch")
				.with(csrf())
				.with(authentication(authenticatedUser(userId)))
				.contentType("application/json")
				.content("{\"rating\":5}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("titulo nao encontrado"));
	}

	@Test
	void markAsWatched_rejectsInvalidRatingWithBadRequest() throws Exception {
		UUID userId = UUID.randomUUID();

		mockMvc.perform(patch("/api/tracking/" + UUID.randomUUID() + "/watch")
				.with(csrf())
				.with(authentication(authenticatedUser(userId)))
				.contentType("application/json")
				.content("{\"rating\":9}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.rating").exists());
	}

	@Test
	void upsertReview_updatesReviewForAuthenticatedUser() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		UUID trackId = UUID.randomUUID();
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.of(couple(coupleId, userId)));
		MediaTrackResponse response = new MediaTrackResponse(
			trackId, 603L, MediaType.MOVIE, MediaStatus.WATCHED, null, 136, null, List.of(), "Matrix", "/poster.jpg", 1999);
		when(userReviewService.upsertReview(eq(trackId), eq(userId), eq(coupleId), any(UpsertReviewRequest.class)))
			.thenReturn(response);

		mockMvc.perform(put("/api/tracking/" + trackId + "/review")
				.with(csrf())
				.with(authentication(authenticatedUser(userId)))
				.contentType("application/json")
				.content("{\"rating\":4,\"opinion\":\"bom\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tmdbId").value(603));
	}

	@Test
	void upsertReview_deniesAccessWithoutAuthentication() throws Exception {
		mockMvc.perform(put("/api/tracking/" + UUID.randomUUID() + "/review")
				.with(csrf())
				.contentType("application/json")
				.content("{\"rating\":4}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void upsertReview_returnsForbiddenWhenTrackNotInUserCouple() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		UUID trackId = UUID.randomUUID();
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.of(couple(coupleId, userId)));
		when(userReviewService.upsertReview(eq(trackId), eq(userId), eq(coupleId), any(UpsertReviewRequest.class)))
			.thenThrow(new AccessDeniedException("titulo nao pertence ao casal do usuario"));

		mockMvc.perform(put("/api/tracking/" + trackId + "/review")
				.with(csrf())
				.with(authentication(authenticatedUser(userId)))
				.contentType("application/json")
				.content("{\"rating\":4}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("titulo nao pertence ao casal do usuario"));
	}

	@Test
	void upsertReview_rejectsInvalidRatingWithBadRequest() throws Exception {
		UUID userId = UUID.randomUUID();

		mockMvc.perform(put("/api/tracking/" + UUID.randomUUID() + "/review")
				.with(csrf())
				.with(authentication(authenticatedUser(userId)))
				.contentType("application/json")
				.content("{\"rating\":0}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.rating").exists());
	}
}
