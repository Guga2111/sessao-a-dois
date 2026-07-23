package com.app.match;

import com.app.couple.Couple;
import com.app.couple.CoupleRepository;
import com.app.media.MediaDetails;
import com.app.media.MediaDetailsService;
import com.app.media.MediaType;
import com.app.tracking.MediaStatus;
import com.app.tracking.MediaTrack;
import com.app.tracking.MediaTrackRepository;
import com.app.tracking.ResourceNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
	private MediaTrackRepository mediaTrackRepository;

	@Mock
	private CoupleRepository coupleRepository;

	@Mock
	private MediaDetailsService mediaDetailsService;

	@Mock
	private SimpMessagingTemplate messagingTemplate;

	private MatchService matchService;

	@BeforeEach
	void setUp() {
		matchService = new MatchService(matchLikeRepository, mediaTrackRepository, coupleRepository,
				mediaDetailsService, messagingTemplate);
	}

	private Couple couple(UUID coupleId) {
		Couple couple = new Couple(UUID.randomUUID(), "ABC234");
		ReflectionTestUtils.setField(couple, "id", coupleId);
		return couple;
	}

	@Test
	void like_firstLikeIsPersistedAndDoesNotMatch() {
		UUID coupleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		LikeRequest request = new LikeRequest(603L, MediaType.MOVIE);

		when(mediaTrackRepository.existsByCoupleIdAndTmdbId(coupleId, 603L)).thenReturn(false);
		when(matchLikeRepository.findByCoupleIdAndUserIdAndTmdbId(coupleId, userId, 603L)).thenReturn(Optional.empty());
		when(coupleRepository.findById(coupleId)).thenReturn(Optional.of(couple(coupleId)));
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
		MatchLike partnerLike = new MatchLike(couple(coupleId), partnerId, 603L, MediaType.MOVIE);

		when(mediaTrackRepository.existsByCoupleIdAndTmdbId(coupleId, 603L)).thenReturn(false, false);
		when(matchLikeRepository.findByCoupleIdAndUserIdAndTmdbId(coupleId, userId, 603L)).thenReturn(Optional.empty());
		when(coupleRepository.findById(coupleId)).thenReturn(Optional.of(couple(coupleId)));
		when(matchLikeRepository.findFirstByCoupleIdAndTmdbIdAndUserIdNot(coupleId, 603L, userId))
			.thenReturn(Optional.of(partnerLike));
		when(mediaDetailsService.getDetails(MediaType.MOVIE, 603L))
			.thenReturn(new MediaDetails(603L, MediaType.MOVIE, "Matrix", 1999, null, null, null, List.of(28), null, null, null));

		LikeResponse response = matchService.like(coupleId, userId, request);

		assertThat(response.matched()).isTrue();
		verify(matchLikeRepository, times(1)).save(any(MatchLike.class));

		ArgumentCaptor<MediaTrack> trackCaptor = ArgumentCaptor.forClass(MediaTrack.class);
		verify(mediaTrackRepository, times(1)).save(trackCaptor.capture());
		assertThat(trackCaptor.getValue().getStatus()).isEqualTo(MediaStatus.WANT_TO_SEE);
		assertThat(trackCaptor.getValue().getTmdbId()).isEqualTo(603L);
		assertThat(trackCaptor.getValue().getGenreIds()).containsExactly(28);

		ArgumentCaptor<MatchEvent> eventCaptor = ArgumentCaptor.forClass(MatchEvent.class);
		verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/couple/" + coupleId + "/match"),
				eventCaptor.capture());
		assertThat(eventCaptor.getValue().title()).isEqualTo("Matrix");
		assertThat(eventCaptor.getValue().tmdbId()).isEqualTo(603L);
	}

	@Test
	void like_doesNotCreateDuplicateMediaTrackWhenAlreadyMatched() {
		UUID coupleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID partnerId = UUID.randomUUID();
		LikeRequest request = new LikeRequest(603L, MediaType.MOVIE);
		MatchLike existingLike = new MatchLike(couple(coupleId), userId, 603L, MediaType.MOVIE);
		MatchLike partnerLike = new MatchLike(couple(coupleId), partnerId, 603L, MediaType.MOVIE);

		when(mediaTrackRepository.existsByCoupleIdAndTmdbId(coupleId, 603L)).thenReturn(false, true);
		when(matchLikeRepository.findByCoupleIdAndUserIdAndTmdbId(coupleId, userId, 603L))
			.thenReturn(Optional.of(existingLike));
		when(matchLikeRepository.findFirstByCoupleIdAndTmdbIdAndUserIdNot(coupleId, 603L, userId))
			.thenReturn(Optional.of(partnerLike));

		LikeResponse response = matchService.like(coupleId, userId, request);

		assertThat(response.matched()).isTrue();
		verify(mediaTrackRepository, never()).save(any(MediaTrack.class));
		verify(messagingTemplate, never()).convertAndSend(anyString(), any(MatchEvent.class));
	}

	@Test
	void like_titleAlreadyTrackedIsRejected() {
		UUID coupleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		LikeRequest request = new LikeRequest(603L, MediaType.MOVIE);

		when(mediaTrackRepository.existsByCoupleIdAndTmdbId(coupleId, 603L)).thenReturn(true);

		assertThatThrownBy(() -> matchService.like(coupleId, userId, request))
			.isInstanceOf(TitleAlreadyTrackedException.class);
		verify(matchLikeRepository, never()).save(any(MatchLike.class));
	}

	@Test
	void like_duplicateLikeFromSameUserDoesNotPersistAgain() {
		UUID coupleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		LikeRequest request = new LikeRequest(603L, MediaType.MOVIE);
		MatchLike existingLike = new MatchLike(couple(coupleId), userId, 603L, MediaType.MOVIE);

		when(mediaTrackRepository.existsByCoupleIdAndTmdbId(coupleId, 603L)).thenReturn(false);
		when(matchLikeRepository.findByCoupleIdAndUserIdAndTmdbId(coupleId, userId, 603L))
			.thenReturn(Optional.of(existingLike));
		when(matchLikeRepository.findFirstByCoupleIdAndTmdbIdAndUserIdNot(coupleId, 603L, userId))
			.thenReturn(Optional.empty());

		LikeResponse response = matchService.like(coupleId, userId, request);

		assertThat(response.matched()).isFalse();
		verify(matchLikeRepository, never()).save(any(MatchLike.class));
		verify(coupleRepository, never()).findById(any(UUID.class));
	}

	@Test
	void like_persistedMatchLikeCapturesUserTitleAndMediaType() {
		UUID coupleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		LikeRequest request = new LikeRequest(1399L, MediaType.TV);

		when(mediaTrackRepository.existsByCoupleIdAndTmdbId(coupleId, 1399L)).thenReturn(false);
		when(matchLikeRepository.findByCoupleIdAndUserIdAndTmdbId(coupleId, userId, 1399L)).thenReturn(Optional.empty());
		when(coupleRepository.findById(coupleId)).thenReturn(Optional.of(couple(coupleId)));
		when(matchLikeRepository.findFirstByCoupleIdAndTmdbIdAndUserIdNot(coupleId, 1399L, userId))
			.thenReturn(Optional.empty());

		matchService.like(coupleId, userId, request);

		ArgumentCaptor<MatchLike> likeCaptor = ArgumentCaptor.forClass(MatchLike.class);
		verify(matchLikeRepository, times(1)).save(likeCaptor.capture());
		MatchLike saved = likeCaptor.getValue();
		assertThat(saved.getUserId()).isEqualTo(userId);
		assertThat(saved.getTmdbId()).isEqualTo(1399L);
		assertThat(saved.getMediaType()).isEqualTo(MediaType.TV);
		assertThat(saved.getCouple().getId()).isEqualTo(coupleId);
	}

	@Test
	void like_matchEventForTvSeriesCarriesMediaTypeAndTitle() {
		UUID coupleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID partnerId = UUID.randomUUID();
		LikeRequest request = new LikeRequest(1399L, MediaType.TV);
		MatchLike partnerLike = new MatchLike(couple(coupleId), partnerId, 1399L, MediaType.TV);

		when(mediaTrackRepository.existsByCoupleIdAndTmdbId(coupleId, 1399L)).thenReturn(false, false);
		when(matchLikeRepository.findByCoupleIdAndUserIdAndTmdbId(coupleId, userId, 1399L)).thenReturn(Optional.empty());
		when(coupleRepository.findById(coupleId)).thenReturn(Optional.of(couple(coupleId)));
		when(matchLikeRepository.findFirstByCoupleIdAndTmdbIdAndUserIdNot(coupleId, 1399L, userId))
			.thenReturn(Optional.of(partnerLike));
		when(mediaDetailsService.getDetails(MediaType.TV, 1399L))
			.thenReturn(new MediaDetails(1399L, MediaType.TV, "Game of Thrones", 2011, null, null, null, List.of(18, 10765), null, null, null));

		LikeResponse response = matchService.like(coupleId, userId, request);

		assertThat(response.matched()).isTrue();

		ArgumentCaptor<MediaTrack> trackCaptor = ArgumentCaptor.forClass(MediaTrack.class);
		verify(mediaTrackRepository, times(1)).save(trackCaptor.capture());
		assertThat(trackCaptor.getValue().getMediaType()).isEqualTo(MediaType.TV);
		assertThat(trackCaptor.getValue().getGenreIds()).containsExactly(18, 10765);

		ArgumentCaptor<MatchEvent> eventCaptor = ArgumentCaptor.forClass(MatchEvent.class);
		verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/couple/" + coupleId + "/match"),
				eventCaptor.capture());
		assertThat(eventCaptor.getValue().mediaType()).isEqualTo(MediaType.TV);
		assertThat(eventCaptor.getValue().title()).isEqualTo("Game of Thrones");
	}

	@Test
	void like_throwsWhenCoupleNotFound() {
		UUID coupleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		LikeRequest request = new LikeRequest(603L, MediaType.MOVIE);

		when(mediaTrackRepository.existsByCoupleIdAndTmdbId(coupleId, 603L)).thenReturn(false);
		when(matchLikeRepository.findByCoupleIdAndUserIdAndTmdbId(coupleId, userId, 603L)).thenReturn(Optional.empty());
		when(coupleRepository.findById(coupleId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> matchService.like(coupleId, userId, request))
			.isInstanceOf(ResourceNotFoundException.class);
	}
}
