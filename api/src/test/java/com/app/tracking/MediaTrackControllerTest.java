package com.app.tracking;

import com.app.couple.Couple;
import com.app.couple.CoupleService;
import com.app.media.MediaType;
import com.app.security.JwtService;
import com.app.security.SecurityConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MediaTrackController.class)
@Import(SecurityConfig.class)
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
	void list_returnsTracksFilteredByStatusForAuthenticatedUser() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.of(couple(coupleId, userId)));
		MediaTrackResponse track = new MediaTrackResponse(
			UUID.randomUUID(), 603L, MediaType.MOVIE, MediaStatus.WATCHING, null, 136, null, List.of());
		when(mediaTrackService.listByStatus(coupleId, MediaStatus.WATCHING)).thenReturn(List.of(track));

		mockMvc.perform(get("/api/tracking").param("status", "WATCHING")
				.with(authentication(authenticatedUser(userId))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].tmdbId").value(603))
			.andExpect(jsonPath("$[0].status").value("WATCHING"));
	}

	@Test
	void create_deniesAccessWithoutAuthentication() throws Exception {
		mockMvc.perform(post("/api/tracking")
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
			UUID.randomUUID(), 603L, MediaType.MOVIE, MediaStatus.WATCHING, null, 136, null, List.of());
		when(mediaTrackService.addTrack(eq(coupleId), eq(userId), any(CreateMediaTrackRequest.class)))
			.thenReturn(response);

		mockMvc.perform(post("/api/tracking")
				.with(authentication(authenticatedUser(userId)))
				.contentType("application/json")
				.content("{\"tmdbId\":603,\"mediaType\":\"MOVIE\",\"status\":\"WATCHING\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.tmdbId").value(603));
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
				.with(authentication(authenticatedUser(userId))))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("titulo nao encontrado"));
	}

	@Test
	void delete_deniesAccessWithoutAuthentication() throws Exception {
		mockMvc.perform(delete("/api/tracking/" + UUID.randomUUID()))
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
			trackId, 603L, MediaType.MOVIE, MediaStatus.WATCHED, null, 136, null, List.of());
		when(mediaTrackService.markAsWatched(eq(trackId), eq(coupleId), eq(userId), any(WatchRequest.class)))
			.thenReturn(response);

		mockMvc.perform(patch("/api/tracking/" + trackId + "/watch")
				.with(authentication(authenticatedUser(userId)))
				.contentType("application/json")
				.content("{\"rating\":5,\"opinion\":\"otimo\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("WATCHED"));
	}

	@Test
	void markAsWatched_deniesAccessWithoutAuthentication() throws Exception {
		mockMvc.perform(patch("/api/tracking/" + UUID.randomUUID() + "/watch")
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
			trackId, 603L, MediaType.MOVIE, MediaStatus.WATCHED, null, 136, null, List.of());
		when(userReviewService.upsertReview(eq(trackId), eq(userId), eq(coupleId), any(UpsertReviewRequest.class)))
			.thenReturn(response);

		mockMvc.perform(put("/api/tracking/" + trackId + "/review")
				.with(authentication(authenticatedUser(userId)))
				.contentType("application/json")
				.content("{\"rating\":4,\"opinion\":\"bom\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tmdbId").value(603));
	}

	@Test
	void upsertReview_deniesAccessWithoutAuthentication() throws Exception {
		mockMvc.perform(put("/api/tracking/" + UUID.randomUUID() + "/review")
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
				.with(authentication(authenticatedUser(userId)))
				.contentType("application/json")
				.content("{\"rating\":0}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.rating").exists());
	}
}
