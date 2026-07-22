package com.app.match;

import com.app.couple.Couple;
import com.app.couple.CoupleRepository;
import com.app.media.MediaType;
import com.app.tracking.MediaTrackRepository;
import com.app.tracking.ResourceNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

	private MatchService matchService;

	@BeforeEach
	void setUp() {
		matchService = new MatchService(matchLikeRepository, mediaTrackRepository, coupleRepository);
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

		when(mediaTrackRepository.existsByCoupleIdAndTmdbId(coupleId, 603L)).thenReturn(false);
		when(matchLikeRepository.findByCoupleIdAndUserIdAndTmdbId(coupleId, userId, 603L)).thenReturn(Optional.empty());
		when(coupleRepository.findById(coupleId)).thenReturn(Optional.of(couple(coupleId)));
		when(matchLikeRepository.findFirstByCoupleIdAndTmdbIdAndUserIdNot(coupleId, 603L, userId))
			.thenReturn(Optional.of(partnerLike));

		LikeResponse response = matchService.like(coupleId, userId, request);

		assertThat(response.matched()).isTrue();
		verify(matchLikeRepository, times(1)).save(any(MatchLike.class));
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
