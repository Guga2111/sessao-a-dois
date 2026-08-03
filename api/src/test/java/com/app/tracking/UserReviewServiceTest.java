package com.app.tracking;

import com.app.couple.Couple;
import com.app.media.MediaType;
import com.app.user.User;
import com.app.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserReviewServiceTest {

	@Mock
	private UserReviewRepository userReviewRepository;

	@Mock
	private MediaTrackRepository mediaTrackRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private MediaTrackMapper mediaTrackMapper;

	@Mock
	private RatingRequestService ratingRequestService;

	private UserReviewService userReviewService;

	@BeforeEach
	void setUp() {
		userReviewService = new UserReviewService(userReviewRepository, mediaTrackRepository, userRepository,
			mediaTrackMapper, ratingRequestService);
	}

	private Couple coupleOwnedBy(UUID coupleId) {
		Couple couple = new Couple(UUID.randomUUID(), "ABC234");
		ReflectionTestUtils.setField(couple, "id", coupleId);
		return couple;
	}

	@Test
	void upsertReviewCreatesReviewWhenNoneExists() {
		UUID trackId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		Couple couple = coupleOwnedBy(coupleId);
		MediaTrack track = new MediaTrack(couple, 603L, MediaType.MOVIE, MediaStatus.WATCHING);
		User user = new User("Ana", "ana@example.com", "hash");
		UpsertReviewRequest request = new UpsertReviewRequest(4, "Gostei bastante");
		MediaTrackResponse expectedResponse = new MediaTrackResponse(
			trackId, 603L, MediaType.MOVIE, MediaStatus.WATCHING, null, null, null, java.util.List.of(), null, null, null);

		when(mediaTrackRepository.findById(trackId)).thenReturn(Optional.of(track));
		when(userReviewRepository.findByMediaTrackIdAndUserId(trackId, userId)).thenReturn(Optional.empty());
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(mediaTrackMapper.toResponse(eq(track), any())).thenReturn(expectedResponse);

		MediaTrackResponse response = userReviewService.upsertReview(trackId, userId, coupleId, request);

		assertThat(response).isEqualTo(expectedResponse);
		assertThat(track.getReviews()).hasSize(1);
		assertThat(track.getReviews().get(0).getRating()).isEqualTo(4);
		assertThat(track.getReviews().get(0).getOpinion()).isEqualTo("Gostei bastante");
		verify(userReviewRepository, times(1)).save(any(UserReview.class));
	}

	@Test
	void upsertReviewUpdatesExistingReviewWithoutCreatingDuplicate() {
		UUID trackId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		Couple couple = coupleOwnedBy(coupleId);
		MediaTrack track = new MediaTrack(couple, 603L, MediaType.MOVIE, MediaStatus.WATCHING);
		User user = new User("Ana", "ana@example.com", "hash");
		UserReview existingReview = new UserReview(track, user, 2, "Regular");
		UpsertReviewRequest request = new UpsertReviewRequest(5, "Mudei de ideia, adorei");
		MediaTrackResponse expectedResponse = new MediaTrackResponse(
			trackId, 603L, MediaType.MOVIE, MediaStatus.WATCHING, null, null, null, java.util.List.of(), null, null, null);

		when(mediaTrackRepository.findById(trackId)).thenReturn(Optional.of(track));
		when(userReviewRepository.findByMediaTrackIdAndUserId(trackId, userId)).thenReturn(Optional.of(existingReview));
		when(mediaTrackMapper.toResponse(eq(track), any())).thenReturn(expectedResponse);

		MediaTrackResponse response = userReviewService.upsertReview(trackId, userId, coupleId, request);

		assertThat(response).isEqualTo(expectedResponse);
		assertThat(existingReview.getRating()).isEqualTo(5);
		assertThat(existingReview.getOpinion()).isEqualTo("Mudei de ideia, adorei");
		assertThat(track.getReviews()).hasSize(0);
		verify(userReviewRepository, times(1)).save(existingReview);
		verify(userRepository, never()).findById(any(UUID.class));
	}

	@Test
	void upsertReviewTriggersRatingRequestOrchestrator() {
		UUID trackId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		Couple couple = coupleOwnedBy(coupleId);
		MediaTrack track = new MediaTrack(couple, 603L, MediaType.MOVIE, MediaStatus.WATCHED);
		User user = new User("Ana", "ana@example.com", "hash");
		UserReview existingReview = new UserReview(track, user, 2, "Regular");
		UpsertReviewRequest request = new UpsertReviewRequest(5, "Adorei");

		when(mediaTrackRepository.findById(trackId)).thenReturn(Optional.of(track));
		when(userReviewRepository.findByMediaTrackIdAndUserId(trackId, userId)).thenReturn(Optional.of(existingReview));

		userReviewService.upsertReview(trackId, userId, coupleId, request);

		verify(ratingRequestService).onRatingRegistered(track, userId);
	}

	@Test
	void upsertReviewThrowsForbiddenWhenTrackBelongsToAnotherCouple() {
		UUID trackId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		Couple otherCouple = coupleOwnedBy(UUID.randomUUID());
		MediaTrack track = new MediaTrack(otherCouple, 603L, MediaType.MOVIE, MediaStatus.WATCHING);
		UpsertReviewRequest request = new UpsertReviewRequest(3, "Ok");

		when(mediaTrackRepository.findById(trackId)).thenReturn(Optional.of(track));

		assertThatThrownBy(() -> userReviewService.upsertReview(trackId, userId, coupleId, request))
			.isInstanceOf(AccessDeniedException.class);

		verify(userReviewRepository, never()).save(any());
	}

	@Test
	void upsertReviewThrowsNotFoundWhenTrackDoesNotExist() {
		UUID trackId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		UpsertReviewRequest request = new UpsertReviewRequest(3, "Ok");

		when(mediaTrackRepository.findById(trackId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userReviewService.upsertReview(trackId, userId, coupleId, request))
			.isInstanceOf(ResourceNotFoundException.class);
	}
}
