package com.app.match;

import com.app.media.MediaDetails;
import com.app.media.MediaDetailsService;
import com.app.media.MediaType;
import com.app.notification.NotificationService;
import com.app.notification.NotificationType;
import com.app.tracking.TrackingFacade;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

	@Mock
	private MatchLikeRepository matchLikeRepository;

	@Mock
	private MatchRejectRepository matchRejectRepository;

	@Mock
	private TrackingFacade trackingFacade;

	@Mock
	private MediaDetailsService mediaDetailsService;

	@Mock
	private SimpMessagingTemplate messagingTemplate;

	@Mock
	private NotificationService notificationService;

	private MatchService matchService;

	@BeforeEach
	void setUp() {
		matchService = new MatchService(matchLikeRepository, matchRejectRepository, trackingFacade,
				mediaDetailsService, messagingTemplate, notificationService);
	}

	@Test
	void like_firstLikeIsPersistedAndDoesNotMatch() {
		UUID coupleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		LikeRequest request = new LikeRequest(603L, MediaType.MOVIE);

		when(trackingFacade.isTracked(coupleId, 603L)).thenReturn(false);
		when(matchLikeRepository.findByCoupleIdAndUserIdAndTmdbId(coupleId, userId, 603L)).thenReturn(Optional.empty());
		when(matchLikeRepository.findFirstByCoupleIdAndTmdbIdAndUserIdNot(coupleId, 603L, userId))
			.thenReturn(Optional.empty());

		LikeResponse response = matchService.like(coupleId, userId, request);

		assertThat(response.matched()).isFalse();
		verify(matchLikeRepository, times(1)).save(any(MatchLike.class));
	}

	@Test
	void like_partnerAlreadyLikedResultsInMatch() {
		UUID coupleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID partnerId = UUID.randomUUID();
		LikeRequest request = new LikeRequest(603L, MediaType.MOVIE);
		MatchLike partnerLike = new MatchLike(coupleId, partnerId, 603L, MediaType.MOVIE);
		MediaDetails details = new MediaDetails(603L, MediaType.MOVIE, "Matrix", 1999, null, null, null, List.of(28), null,
				null, null);

		when(trackingFacade.isTracked(coupleId, 603L)).thenReturn(false, false);
		when(matchLikeRepository.findByCoupleIdAndUserIdAndTmdbId(coupleId, userId, 603L)).thenReturn(Optional.empty());
		when(matchLikeRepository.findFirstByCoupleIdAndTmdbIdAndUserIdNot(coupleId, 603L, userId))
			.thenReturn(Optional.of(partnerLike));
		when(mediaDetailsService.getDetails(MediaType.MOVIE, 603L)).thenReturn(details);

		LikeResponse response = matchService.like(coupleId, userId, request);

		assertThat(response.matched()).isTrue();
		verify(matchLikeRepository, times(1)).save(any(MatchLike.class));

		verify(trackingFacade, times(1)).createTrackFromMatch(eq(coupleId), eq(603L), eq(MediaType.MOVIE), eq(details));
		verify(mediaDetailsService, times(1)).getDetails(MediaType.MOVIE, 603L);

		ArgumentCaptor<MatchEvent> eventCaptor = ArgumentCaptor.forClass(MatchEvent.class);
		verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/couple/" + coupleId + "/match"),
				eventCaptor.capture());
		assertThat(eventCaptor.getValue().title()).isEqualTo("Matrix");
		assertThat(eventCaptor.getValue().tmdbId()).isEqualTo(603L);

		verify(notificationService, times(1)).notifyCouple(eq(coupleId), eq(NotificationType.MATCH), eq(603L),
				eq(MediaType.MOVIE), eq("Matrix"), eq(userId));
	}

	@Test
	void like_doesNotCreateDuplicateMediaTrackWhenAlreadyMatched() {
		UUID coupleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID partnerId = UUID.randomUUID();
		LikeRequest request = new LikeRequest(603L, MediaType.MOVIE);
		MatchLike existingLike = new MatchLike(coupleId, userId, 603L, MediaType.MOVIE);
		MatchLike partnerLike = new MatchLike(coupleId, partnerId, 603L, MediaType.MOVIE);

		when(trackingFacade.isTracked(coupleId, 603L)).thenReturn(false, true);
		when(matchLikeRepository.findByCoupleIdAndUserIdAndTmdbId(coupleId, userId, 603L))
			.thenReturn(Optional.of(existingLike));
		when(matchLikeRepository.findFirstByCoupleIdAndTmdbIdAndUserIdNot(coupleId, 603L, userId))
			.thenReturn(Optional.of(partnerLike));

		LikeResponse response = matchService.like(coupleId, userId, request);

		assertThat(response.matched()).isTrue();
		verify(trackingFacade, never()).createTrackFromMatch(any(UUID.class), any(Long.class), any(MediaType.class),
				any(MediaDetails.class));
		verify(messagingTemplate, never()).convertAndSend(anyString(), any(MatchEvent.class));
		verify(notificationService, never()).notifyCouple(any(UUID.class), any(NotificationType.class), any(Long.class),
				any(MediaType.class), anyString(), any(UUID.class));
	}

	@Test
	void like_titleAlreadyTrackedIsRejected() {
		UUID coupleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		LikeRequest request = new LikeRequest(603L, MediaType.MOVIE);

		when(trackingFacade.isTracked(coupleId, 603L)).thenReturn(true);

		assertThatThrownBy(() -> matchService.like(coupleId, userId, request))
			.isInstanceOf(TitleAlreadyTrackedException.class);
		verify(matchLikeRepository, never()).save(any(MatchLike.class));
	}

	@Test
	void like_duplicateLikeFromSameUserDoesNotPersistAgain() {
		UUID coupleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		LikeRequest request = new LikeRequest(603L, MediaType.MOVIE);
		MatchLike existingLike = new MatchLike(coupleId, userId, 603L, MediaType.MOVIE);

		when(trackingFacade.isTracked(coupleId, 603L)).thenReturn(false);
		when(matchLikeRepository.findByCoupleIdAndUserIdAndTmdbId(coupleId, userId, 603L))
			.thenReturn(Optional.of(existingLike));
		when(matchLikeRepository.findFirstByCoupleIdAndTmdbIdAndUserIdNot(coupleId, 603L, userId))
			.thenReturn(Optional.empty());

		LikeResponse response = matchService.like(coupleId, userId, request);

		assertThat(response.matched()).isFalse();
		verify(matchLikeRepository, never()).save(any(MatchLike.class));
	}

	@Test
	void like_persistedMatchLikeCapturesUserTitleAndMediaType() {
		UUID coupleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		LikeRequest request = new LikeRequest(1399L, MediaType.TV);

		when(trackingFacade.isTracked(coupleId, 1399L)).thenReturn(false);
		when(matchLikeRepository.findByCoupleIdAndUserIdAndTmdbId(coupleId, userId, 1399L)).thenReturn(Optional.empty());
		when(matchLikeRepository.findFirstByCoupleIdAndTmdbIdAndUserIdNot(coupleId, 1399L, userId))
			.thenReturn(Optional.empty());

		matchService.like(coupleId, userId, request);

		ArgumentCaptor<MatchLike> likeCaptor = ArgumentCaptor.forClass(MatchLike.class);
		verify(matchLikeRepository, times(1)).save(likeCaptor.capture());
		MatchLike saved = likeCaptor.getValue();
		assertThat(saved.getUserId()).isEqualTo(userId);
		assertThat(saved.getTmdbId()).isEqualTo(1399L);
		assertThat(saved.getMediaType()).isEqualTo(MediaType.TV);
		assertThat(saved.getCoupleId()).isEqualTo(coupleId);
		assertThat(saved.getTitle()).isNull();
		assertThat(saved.getPosterUrl()).isNull();
		assertThat(saved.getReleaseYear()).isNull();
		verify(mediaDetailsService, never()).getDetails(any(), anyLong());
	}

	@Test
	void like_persistsProvidedMetadataOnMatchLikeWithoutCallingTmdb() {
		UUID coupleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		LikeRequest request = new LikeRequest(1399L, MediaType.TV, "Game of Thrones", "/got.jpg", 2011);

		when(trackingFacade.isTracked(coupleId, 1399L)).thenReturn(false);
		when(matchLikeRepository.findByCoupleIdAndUserIdAndTmdbId(coupleId, userId, 1399L)).thenReturn(Optional.empty());
		when(matchLikeRepository.findFirstByCoupleIdAndTmdbIdAndUserIdNot(coupleId, 1399L, userId))
			.thenReturn(Optional.empty());

		matchService.like(coupleId, userId, request);

		ArgumentCaptor<MatchLike> likeCaptor = ArgumentCaptor.forClass(MatchLike.class);
		verify(matchLikeRepository, times(1)).save(likeCaptor.capture());
		MatchLike saved = likeCaptor.getValue();
		assertThat(saved.getTitle()).isEqualTo("Game of Thrones");
		assertThat(saved.getPosterUrl()).isEqualTo("/got.jpg");
		assertThat(saved.getReleaseYear()).isEqualTo(2011);
		verify(mediaDetailsService, never()).getDetails(any(), anyLong());
	}

	@Test
	void like_matchEventForTvSeriesCarriesMediaTypeAndTitle() {
		UUID coupleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID partnerId = UUID.randomUUID();
		LikeRequest request = new LikeRequest(1399L, MediaType.TV);
		MatchLike partnerLike = new MatchLike(coupleId, partnerId, 1399L, MediaType.TV);

		when(trackingFacade.isTracked(coupleId, 1399L)).thenReturn(false, false);
		when(matchLikeRepository.findByCoupleIdAndUserIdAndTmdbId(coupleId, userId, 1399L)).thenReturn(Optional.empty());
		when(matchLikeRepository.findFirstByCoupleIdAndTmdbIdAndUserIdNot(coupleId, 1399L, userId))
			.thenReturn(Optional.of(partnerLike));
		when(mediaDetailsService.getDetails(MediaType.TV, 1399L))
			.thenReturn(new MediaDetails(1399L, MediaType.TV, "Game of Thrones", 2011, null, null, null, List.of(18, 10765), null, null, null));

		LikeResponse response = matchService.like(coupleId, userId, request);

		assertThat(response.matched()).isTrue();

		ArgumentCaptor<MediaDetails> detailsCaptor = ArgumentCaptor.forClass(MediaDetails.class);
		verify(trackingFacade, times(1)).createTrackFromMatch(eq(coupleId), eq(1399L), eq(MediaType.TV),
				detailsCaptor.capture());
		assertThat(detailsCaptor.getValue().genreIds()).containsExactly(18, 10765);

		ArgumentCaptor<MatchEvent> eventCaptor = ArgumentCaptor.forClass(MatchEvent.class);
		verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/couple/" + coupleId + "/match"),
				eventCaptor.capture());
		assertThat(eventCaptor.getValue().mediaType()).isEqualTo(MediaType.TV);
		assertThat(eventCaptor.getValue().title()).isEqualTo("Game of Thrones");

		verify(notificationService, times(1)).notifyCouple(eq(coupleId), eq(NotificationType.MATCH), eq(1399L),
				eq(MediaType.TV), eq("Game of Thrones"), eq(userId));
	}

	@Test
	void reject_persistsNewReject() {
		UUID coupleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		LikeRequest request = new LikeRequest(603L, MediaType.MOVIE);

		when(matchRejectRepository.findByCoupleIdAndUserIdAndTmdbId(coupleId, userId, 603L))
			.thenReturn(Optional.empty());
		when(matchLikeRepository.findFirstByCoupleIdAndTmdbIdAndUserIdNot(coupleId, 603L, userId))
			.thenReturn(Optional.empty());

		matchService.reject(coupleId, userId, request);

		ArgumentCaptor<MatchReject> captor = ArgumentCaptor.forClass(MatchReject.class);
		verify(matchRejectRepository, times(1)).save(captor.capture());
		assertThat(captor.getValue().getTmdbId()).isEqualTo(603L);
		assertThat(captor.getValue().getUserId()).isEqualTo(userId);
		verify(notificationService, never()).notifyCouple(any(UUID.class), any(NotificationType.class), any(Long.class),
				any(MediaType.class), anyString(), any(UUID.class));
	}

	@Test
	void reject_partnerAlreadyLikedCreatesNoMatchNotification() {
		UUID coupleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID partnerId = UUID.randomUUID();
		LikeRequest request = new LikeRequest(603L, MediaType.MOVIE);
		MatchLike partnerLike = new MatchLike(coupleId, partnerId, 603L, MediaType.MOVIE);

		when(matchRejectRepository.findByCoupleIdAndUserIdAndTmdbId(coupleId, userId, 603L))
			.thenReturn(Optional.empty());
		when(matchLikeRepository.findFirstByCoupleIdAndTmdbIdAndUserIdNot(coupleId, 603L, userId))
			.thenReturn(Optional.of(partnerLike));
		when(mediaDetailsService.getDetails(MediaType.MOVIE, 603L))
			.thenReturn(new MediaDetails(603L, MediaType.MOVIE, "Matrix", 1999, null, null, null, List.of(28), null, null, null));

		matchService.reject(coupleId, userId, request);

		verify(notificationService, times(1)).notifyCouple(eq(coupleId), eq(NotificationType.NO_MATCH), eq(603L),
				eq(MediaType.MOVIE), eq("Matrix"), eq(userId));
	}

	@Test
	void reject_idempotentWhenAlreadyRejected() {
		UUID coupleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		LikeRequest request = new LikeRequest(603L, MediaType.MOVIE);
		MatchReject existing = new MatchReject(coupleId, userId, 603L, MediaType.MOVIE);

		when(matchRejectRepository.findByCoupleIdAndUserIdAndTmdbId(coupleId, userId, 603L))
			.thenReturn(Optional.of(existing));

		matchService.reject(coupleId, userId, request);

		verify(matchRejectRepository, never()).save(any(MatchReject.class));
	}

	@Test
	void getPending_returnsPendingFromPartnerLikes() {
		UUID coupleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID partnerId = UUID.randomUUID();
		MatchLike partnerLike = new MatchLike(coupleId, partnerId, 603L, MediaType.MOVIE);

		when(matchLikeRepository.findPendingForUser(eq(coupleId), eq(userId), any(Pageable.class)))
			.thenReturn(new PageImpl<>(List.of(partnerLike)));
		when(mediaDetailsService.getDetails(MediaType.MOVIE, 603L))
			.thenReturn(new MediaDetails(603L, MediaType.MOVIE, "Matrix", 1999, "/poster.jpg", null, null, List.of(28), null, null, null));

		List<PendingMatchDto> result = matchService.getPending(coupleId, userId);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).tmdbId()).isEqualTo(603L);
		assertThat(result.get(0).title()).isEqualTo("Matrix");
		assertThat(result.get(0).posterUrl()).isEqualTo("/poster.jpg");
		assertThat(result.get(0).releaseYear()).isEqualTo(1999);
	}

	@Test
	void getPending_usesPersistedMetadataWithoutCallingTmdb() {
		UUID coupleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID partnerId = UUID.randomUUID();
		MatchLike partnerLike = new MatchLike(coupleId, partnerId, 603L, MediaType.MOVIE);
		partnerLike.setTitle("Matrix");
		partnerLike.setPosterUrl("/poster.jpg");
		partnerLike.setReleaseYear(1999);

		when(matchLikeRepository.findPendingForUser(eq(coupleId), eq(userId), any(Pageable.class)))
			.thenReturn(new PageImpl<>(List.of(partnerLike)));

		List<PendingMatchDto> result = matchService.getPending(coupleId, userId);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).tmdbId()).isEqualTo(603L);
		assertThat(result.get(0).title()).isEqualTo("Matrix");
		assertThat(result.get(0).posterUrl()).isEqualTo("/poster.jpg");
		assertThat(result.get(0).releaseYear()).isEqualTo(1999);
		verify(mediaDetailsService, never()).getDetails(any(), anyLong());
	}

	@Test
	void getPending_returnsRowWithNullFieldsWhenTmdbFails() {
		UUID coupleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID partnerId = UUID.randomUUID();
		MatchLike partnerLike = new MatchLike(coupleId, partnerId, 603L, MediaType.MOVIE);

		when(matchLikeRepository.findPendingForUser(eq(coupleId), eq(userId), any(Pageable.class)))
			.thenReturn(new PageImpl<>(List.of(partnerLike)));
		when(mediaDetailsService.getDetails(MediaType.MOVIE, 603L))
			.thenThrow(new RuntimeException("TMDB unavailable"));

		List<PendingMatchDto> result = matchService.getPending(coupleId, userId);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).tmdbId()).isEqualTo(603L);
		assertThat(result.get(0).title()).isNull();
		assertThat(result.get(0).posterUrl()).isNull();
		assertThat(result.get(0).releaseYear()).isNull();
		verify(matchLikeRepository, never()).save(any(MatchLike.class));
	}

	@Test
	void getPending_healsHistoricalRowAndPersistsMetadataOnlyOnce() {
		UUID coupleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID partnerId = UUID.randomUUID();
		MatchLike partnerLike = new MatchLike(coupleId, partnerId, 603L, MediaType.MOVIE);

		when(matchLikeRepository.findPendingForUser(eq(coupleId), eq(userId), any(Pageable.class)))
			.thenReturn(new PageImpl<>(List.of(partnerLike)));
		when(mediaDetailsService.getDetails(MediaType.MOVIE, 603L))
			.thenReturn(new MediaDetails(603L, MediaType.MOVIE, "Matrix", 1999, "/poster.jpg", null, null, List.of(28), null, null, null));

		List<PendingMatchDto> first = matchService.getPending(coupleId, userId);
		List<PendingMatchDto> second = matchService.getPending(coupleId, userId);

		assertThat(first.get(0).title()).isEqualTo("Matrix");
		assertThat(second.get(0).title()).isEqualTo("Matrix");
		verify(matchLikeRepository, times(1)).save(partnerLike);
		verify(mediaDetailsService, times(1)).getDetails(MediaType.MOVIE, 603L);
	}

	@Test
	void getPending_returnsEmptyListWhenNoPending() {
		UUID coupleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		when(matchLikeRepository.findPendingForUser(eq(coupleId), eq(userId), any(Pageable.class)))
			.thenReturn(new PageImpl<>(List.of()));

		List<PendingMatchDto> result = matchService.getPending(coupleId, userId);

		assertThat(result).isEmpty();
	}
}
