package com.app.tracking;

import com.app.couple.Couple;
import com.app.couple.CoupleRepository;
import com.app.media.MediaDetails;
import com.app.media.MediaDetailsService;
import com.app.media.MediaType;
import com.app.user.User;
import com.app.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
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
class MediaTrackServiceTest {

	@Mock
	private MediaTrackRepository mediaTrackRepository;

	@Mock
	private CoupleRepository coupleRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private MediaDetailsService mediaDetailsService;

	@Mock
	private RatingRequestService ratingRequestService;

	private MediaTrackService mediaTrackService;

	@BeforeEach
	void setUp() {
		mediaTrackService = new MediaTrackService(mediaTrackRepository, coupleRepository, userRepository,
				mediaDetailsService, ratingRequestService, new MediaTrackMapper());
	}

	private Couple coupleWithMembers(UUID user1Id, UUID user2Id) {
		Couple couple = new Couple(user1Id, "ABC234");
		couple.setUser2Id(user2Id);
		return couple;
	}

	@Test
	void addTrackCreatesTrackAndInitialReview() {
		UUID userId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		Couple couple = coupleWithMembers(userId, UUID.randomUUID());
		User user = new User("Ana", "ana@example.com", "hash");
		ReflectionTestUtils.setField(user, "id", userId);
		CreateMediaTrackRequest request = new CreateMediaTrackRequest(
			603L, MediaType.MOVIE, MediaStatus.WATCHING, null, 136, null, null);

		when(coupleRepository.findById(coupleId)).thenReturn(Optional.of(couple));
		when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(user));
		when(mediaDetailsService.getDetails(MediaType.MOVIE, 603L))
			.thenReturn(new MediaDetails(603L, MediaType.MOVIE, "Matrix", 1999, null, null, null,
					List.of(28, 12), null, 136, null));
		ArgumentCaptor<MediaTrack> trackCaptor = ArgumentCaptor.forClass(MediaTrack.class);
		when(mediaTrackRepository.save(trackCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

		MediaTrackResponse response = mediaTrackService.addTrack(coupleId, userId, request);

		assertThat(response.tmdbId()).isEqualTo(603L);
		assertThat(response.mediaType()).isEqualTo(MediaType.MOVIE);
		assertThat(response.status()).isEqualTo(MediaStatus.WATCHING);
		assertThat(response.runtime()).isEqualTo(136);
		assertThat(response.reviews()).hasSize(2);
		assertThat(response.reviews())
			.anySatisfy(review -> {
				assertThat(review.userId()).isEqualTo(userId);
				assertThat(review.rating()).isNull();
				assertThat(review.opinion()).isNull();
			});
		assertThat(trackCaptor.getValue().getGenreIds()).containsExactly(28, 12);
	}

	@Test
	void addTrackSavesEmptyGenresWhenTmdbFails() {
		UUID userId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		Couple couple = coupleWithMembers(userId, UUID.randomUUID());
		User user = new User("Ana", "ana@example.com", "hash");
		ReflectionTestUtils.setField(user, "id", userId);
		CreateMediaTrackRequest request = new CreateMediaTrackRequest(
			603L, MediaType.MOVIE, MediaStatus.WATCHING, null, 136, null, null);

		when(coupleRepository.findById(coupleId)).thenReturn(Optional.of(couple));
		when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(user));
		when(mediaDetailsService.getDetails(MediaType.MOVIE, 603L))
			.thenThrow(new RuntimeException("TMDB indisponivel"));
		ArgumentCaptor<MediaTrack> trackCaptor = ArgumentCaptor.forClass(MediaTrack.class);
		when(mediaTrackRepository.save(trackCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

		MediaTrackResponse response = mediaTrackService.addTrack(coupleId, userId, request);

		assertThat(response.tmdbId()).isEqualTo(603L);
		assertThat(trackCaptor.getValue().getGenreIds()).isEmpty();
	}

	@Test
	void addTrackThrowsWhenCoupleNotFound() {
		UUID userId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		CreateMediaTrackRequest request = new CreateMediaTrackRequest(
			603L, MediaType.MOVIE, MediaStatus.WATCHING, null, null, null, null);

		when(coupleRepository.findById(coupleId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> mediaTrackService.addTrack(coupleId, userId, request))
			.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void listByStatusReturnsIndependentReviewsForBothMembers() {
		UUID coupleId = UUID.randomUUID();
		UUID user1Id = UUID.randomUUID();
		UUID user2Id = UUID.randomUUID();
		Couple couple = coupleWithMembers(user1Id, user2Id);
		ReflectionTestUtils.setField(couple, "id", coupleId);

		User user1 = new User("Ana", "ana@example.com", "hash");
		ReflectionTestUtils.setField(user1, "id", user1Id);
		User user2 = new User("Bob", "bob@example.com", "hash");
		ReflectionTestUtils.setField(user2, "id", user2Id);

		MediaTrack track = new MediaTrack(couple, 603L, MediaType.MOVIE, MediaStatus.WATCHED);
		track.getReviews().add(new UserReview(track, user1, 5, "Ana adorou"));
		track.getReviews().add(new UserReview(track, user2, 2, "Bob nem tanto"));

		when(mediaTrackRepository.findByCoupleId(coupleId)).thenReturn(List.of(track));
		when(userRepository.findAllById(any())).thenReturn(List.of(user1, user2));

		List<MediaTrackResponse> responses = mediaTrackService.listByStatus(coupleId, null);

		assertThat(responses).hasSize(1);
		List<ReviewDto> reviews = responses.get(0).reviews();
		assertThat(reviews).hasSize(2);
		assertThat(reviews)
			.anySatisfy(review -> {
				assertThat(review.userId()).isEqualTo(user1Id);
				assertThat(review.userName()).isEqualTo("Ana");
				assertThat(review.rating()).isEqualTo(5);
				assertThat(review.opinion()).isEqualTo("Ana adorou");
			})
			.anySatisfy(review -> {
				assertThat(review.userId()).isEqualTo(user2Id);
				assertThat(review.userName()).isEqualTo("Bob");
				assertThat(review.rating()).isEqualTo(2);
				assertThat(review.opinion()).isEqualTo("Bob nem tanto");
			});
	}

	@Test
	void listByStatusFiltersByStatusWhenProvided() {
		UUID coupleId = UUID.randomUUID();
		UUID user1Id = UUID.randomUUID();
		Couple couple = coupleWithMembers(user1Id, null);
		ReflectionTestUtils.setField(couple, "id", coupleId);
		User user1 = new User("Ana", "ana@example.com", "hash");
		ReflectionTestUtils.setField(user1, "id", user1Id);
		MediaTrack track = new MediaTrack(couple, 603L, MediaType.MOVIE, MediaStatus.WANT_TO_SEE);

		when(mediaTrackRepository.findByCoupleIdAndStatus(coupleId, MediaStatus.WANT_TO_SEE))
			.thenReturn(List.of(track));
		when(userRepository.findAllById(any())).thenReturn(List.of(user1));

		List<MediaTrackResponse> responses = mediaTrackService.listByStatus(coupleId, MediaStatus.WANT_TO_SEE);

		assertThat(responses).hasSize(1);
		assertThat(responses.get(0).status()).isEqualTo(MediaStatus.WANT_TO_SEE);
		assertThat(responses.get(0).reviews()).hasSize(1);
		verify(mediaTrackRepository, never()).findByCoupleId(any(UUID.class));
	}

	@Test
	void listByStatusPagedReturnsPageMappedFromRepository() {
		UUID coupleId = UUID.randomUUID();
		UUID user1Id = UUID.randomUUID();
		UUID trackId = UUID.randomUUID();
		Couple couple = coupleWithMembers(user1Id, null);
		ReflectionTestUtils.setField(couple, "id", coupleId);
		User user1 = new User("Ana", "ana@example.com", "hash");
		ReflectionTestUtils.setField(user1, "id", user1Id);
		MediaTrack track = new MediaTrack(couple, 603L, MediaType.MOVIE, MediaStatus.WATCHING);
		ReflectionTestUtils.setField(track, "id", trackId);

		Page<UUID> idPage = new PageImpl<>(List.of(trackId), PageRequest.of(0, 20), 1);
		when(mediaTrackRepository.findIdsByCoupleIdAndStatusOrderByCreatedAtDesc(
				eq(coupleId), eq(MediaStatus.WATCHING), any(Pageable.class)))
			.thenReturn(idPage);
		when(mediaTrackRepository.findByIdIn(List.of(trackId))).thenReturn(List.of(track));
		when(userRepository.findAllById(any())).thenReturn(List.of(user1));

		Page<MediaTrackResponse> result = mediaTrackService.listByStatusPaged(coupleId, MediaStatus.WATCHING, 0, 20);

		assertThat(result.getTotalElements()).isEqualTo(1);
		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().get(0).tmdbId()).isEqualTo(603L);
	}

	@Test
	void listByStatusPagedClampsSizeToServerMaximum() {
		UUID coupleId = UUID.randomUUID();
		Page<UUID> idPage = new PageImpl<>(List.of());
		ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
		when(mediaTrackRepository.findIdsByCoupleIdAndStatusOrderByCreatedAtDesc(
				eq(coupleId), eq(MediaStatus.WATCHED), pageableCaptor.capture()))
			.thenReturn(idPage);
		when(mediaTrackRepository.findByIdIn(any())).thenReturn(List.of());

		mediaTrackService.listByStatusPaged(coupleId, MediaStatus.WATCHED, 0, 500);

		assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
	}

	@Test
	void listByStatusPagedDefaultsNegativePageToZero() {
		UUID coupleId = UUID.randomUUID();
		Page<UUID> idPage = new PageImpl<>(List.of());
		ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
		when(mediaTrackRepository.findIdsByCoupleIdAndStatusOrderByCreatedAtDesc(
				eq(coupleId), eq(MediaStatus.WATCHED), pageableCaptor.capture()))
			.thenReturn(idPage);
		when(mediaTrackRepository.findByIdIn(any())).thenReturn(List.of());

		mediaTrackService.listByStatusPaged(coupleId, MediaStatus.WATCHED, -3, 20);

		assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
	}

	@Test
	void markAsWatchedUpdatesStatusAndExistingReview() {
		UUID trackId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		Couple couple = coupleWithMembers(userId, UUID.randomUUID());
		ReflectionTestUtils.setField(couple, "id", coupleId);
		User user = new User("Ana", "ana@example.com", "hash");
		ReflectionTestUtils.setField(user, "id", userId);
		MediaTrack track = new MediaTrack(couple, 603L, MediaType.MOVIE, MediaStatus.WATCHING);
		track.getReviews().add(new UserReview(track, user, 2, "Regular"));
		WatchRequest request = new WatchRequest(5, "Melhorou muito");

		when(mediaTrackRepository.findById(trackId)).thenReturn(Optional.of(track));
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(mediaTrackRepository.save(any(MediaTrack.class))).thenAnswer(invocation -> invocation.getArgument(0));

		MediaTrackResponse response = mediaTrackService.markAsWatched(trackId, coupleId, userId, request);

		assertThat(response.status()).isEqualTo(MediaStatus.WATCHED);
		assertThat(response.watchedDate()).isEqualTo(LocalDate.now());
		assertThat(track.getStatus()).isEqualTo(MediaStatus.WATCHED);
		assertThat(track.getReviews()).hasSize(1);
		assertThat(track.getReviews().get(0).getRating()).isEqualTo(5);
		assertThat(track.getReviews().get(0).getOpinion()).isEqualTo("Melhorou muito");
	}

	@Test
	void markAsWatchedAddsReviewWhenUserHasNone() {
		UUID trackId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		Couple couple = coupleWithMembers(userId, UUID.randomUUID());
		ReflectionTestUtils.setField(couple, "id", coupleId);
		User user = new User("Ana", "ana@example.com", "hash");
		ReflectionTestUtils.setField(user, "id", userId);
		MediaTrack track = new MediaTrack(couple, 603L, MediaType.MOVIE, MediaStatus.WANT_TO_SEE);
		WatchRequest request = new WatchRequest(4, "Curti");

		when(mediaTrackRepository.findById(trackId)).thenReturn(Optional.of(track));
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(mediaTrackRepository.save(any(MediaTrack.class))).thenAnswer(invocation -> invocation.getArgument(0));

		mediaTrackService.markAsWatched(trackId, coupleId, userId, request);

		assertThat(track.getStatus()).isEqualTo(MediaStatus.WATCHED);
		assertThat(track.getReviews()).hasSize(1);
		assertThat(track.getReviews().get(0).getUser().getId()).isEqualTo(userId);
		assertThat(track.getReviews().get(0).getRating()).isEqualTo(4);
		assertThat(track.getReviews().get(0).getOpinion()).isEqualTo("Curti");
	}

	@Test
	void markAsWatchedTriggersRatingRequestOrchestrator() {
		UUID trackId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		Couple couple = coupleWithMembers(userId, UUID.randomUUID());
		ReflectionTestUtils.setField(couple, "id", coupleId);
		User user = new User("Ana", "ana@example.com", "hash");
		ReflectionTestUtils.setField(user, "id", userId);
		MediaTrack track = new MediaTrack(couple, 603L, MediaType.MOVIE, MediaStatus.WANT_TO_SEE);
		WatchRequest request = new WatchRequest(4, "Curti");

		when(mediaTrackRepository.findById(trackId)).thenReturn(Optional.of(track));
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(mediaTrackRepository.save(any(MediaTrack.class))).thenAnswer(invocation -> invocation.getArgument(0));

		mediaTrackService.markAsWatched(trackId, coupleId, userId, request);

		verify(ratingRequestService).onRatingRegistered(track, userId);
	}

	@Test
	void addTrackTriggersRatingRequestOrchestratorWhenWatched() {
		UUID userId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		Couple couple = coupleWithMembers(userId, UUID.randomUUID());
		User user = new User("Ana", "ana@example.com", "hash");
		ReflectionTestUtils.setField(user, "id", userId);
		CreateMediaTrackRequest request = new CreateMediaTrackRequest(
			603L, MediaType.MOVIE, MediaStatus.WATCHED, LocalDate.now(), 136, 5, "Otimo");

		when(coupleRepository.findById(coupleId)).thenReturn(Optional.of(couple));
		when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(user));
		when(mediaDetailsService.getDetails(MediaType.MOVIE, 603L))
			.thenReturn(new MediaDetails(603L, MediaType.MOVIE, "Matrix", 1999, null, null, null,
					List.of(28), null, 136, null));
		ArgumentCaptor<MediaTrack> trackCaptor = ArgumentCaptor.forClass(MediaTrack.class);
		when(mediaTrackRepository.save(trackCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

		mediaTrackService.addTrack(coupleId, userId, request);

		verify(ratingRequestService).onRatingRegistered(trackCaptor.getValue(), userId);
	}

	@Test
	void addTrackDoesNotTriggerRatingRequestWhenNotWatched() {
		UUID userId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		Couple couple = coupleWithMembers(userId, UUID.randomUUID());
		User user = new User("Ana", "ana@example.com", "hash");
		ReflectionTestUtils.setField(user, "id", userId);
		CreateMediaTrackRequest request = new CreateMediaTrackRequest(
			603L, MediaType.MOVIE, MediaStatus.WANT_TO_SEE, null, null, null, null);

		when(coupleRepository.findById(coupleId)).thenReturn(Optional.of(couple));
		when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(user));
		when(mediaDetailsService.getDetails(MediaType.MOVIE, 603L))
			.thenReturn(new MediaDetails(603L, MediaType.MOVIE, "Matrix", 1999, null, null, null,
					List.of(28), null, 136, null));
		when(mediaTrackRepository.save(any(MediaTrack.class))).thenAnswer(invocation -> invocation.getArgument(0));

		mediaTrackService.addTrack(coupleId, userId, request);

		verify(ratingRequestService, never()).onRatingRegistered(any(), any());
	}

	@Test
	void addTrackRejectsRatingWhenStatusIsNotWatched() {
		UUID userId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		CreateMediaTrackRequest request = new CreateMediaTrackRequest(
			603L, MediaType.MOVIE, MediaStatus.WATCHING, null, null, 5, null);

		assertThatThrownBy(() -> mediaTrackService.addTrack(coupleId, userId, request))
			.isInstanceOf(IllegalArgumentException.class);

		verify(mediaTrackRepository, never()).save(any());
	}

	@Test
	void markAsWatchedThrowsResourceNotFoundWhenTrackBelongsToAnotherCouple() {
		UUID trackId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID otherCoupleUser = UUID.randomUUID();
		Couple otherCouple = coupleWithMembers(otherCoupleUser, UUID.randomUUID());
		ReflectionTestUtils.setField(otherCouple, "id", UUID.randomUUID());
		MediaTrack track = new MediaTrack(otherCouple, 603L, MediaType.MOVIE, MediaStatus.WATCHING);
		WatchRequest request = new WatchRequest(5, "Otimo");

		when(mediaTrackRepository.findById(trackId)).thenReturn(Optional.of(track));

		assertThatThrownBy(() -> mediaTrackService.markAsWatched(trackId, coupleId, userId, request))
			.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void markAsWatchedThrowsResourceNotFoundWhenTrackDoesNotExist() {
		UUID trackId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		WatchRequest request = new WatchRequest(5, "Otimo");

		when(mediaTrackRepository.findById(trackId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> mediaTrackService.markAsWatched(trackId, coupleId, userId, request))
			.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void deleteTrackRemovesOwnedTrack() {
		UUID trackId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		Couple couple = coupleWithMembers(UUID.randomUUID(), UUID.randomUUID());
		ReflectionTestUtils.setField(couple, "id", coupleId);
		MediaTrack track = new MediaTrack(couple, 603L, MediaType.MOVIE, MediaStatus.WATCHING);

		when(mediaTrackRepository.findById(trackId)).thenReturn(Optional.of(track));

		mediaTrackService.deleteTrack(trackId, coupleId);

		verify(ratingRequestService).onTrackDeleted(track);
		verify(mediaTrackRepository, times(1)).delete(track);
	}

	@Test
	void deleteTrackThrowsWhenTrackNotFound() {
		UUID trackId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();

		when(mediaTrackRepository.findById(trackId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> mediaTrackService.deleteTrack(trackId, coupleId))
			.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void deleteTrackThrowsResourceNotFoundWhenTrackBelongsToAnotherCouple() {
		UUID trackId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		Couple otherCouple = coupleWithMembers(UUID.randomUUID(), UUID.randomUUID());
		ReflectionTestUtils.setField(otherCouple, "id", UUID.randomUUID());
		MediaTrack track = new MediaTrack(otherCouple, 603L, MediaType.MOVIE, MediaStatus.WATCHING);

		when(mediaTrackRepository.findById(trackId)).thenReturn(Optional.of(track));

		assertThatThrownBy(() -> mediaTrackService.deleteTrack(trackId, coupleId))
			.isInstanceOf(ResourceNotFoundException.class);
		verify(mediaTrackRepository, never()).delete(any(MediaTrack.class));
		verify(ratingRequestService, never()).onTrackDeleted(any());
	}

	@Test
	void startWatchingMovesTrackFromWantToSeeToWatching() {
		UUID trackId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		Couple couple = coupleWithMembers(UUID.randomUUID(), UUID.randomUUID());
		ReflectionTestUtils.setField(couple, "id", coupleId);
		MediaTrack track = new MediaTrack(couple, 603L, MediaType.MOVIE, MediaStatus.WANT_TO_SEE);

		when(mediaTrackRepository.findById(trackId)).thenReturn(Optional.of(track));
		when(mediaTrackRepository.save(any(MediaTrack.class))).thenAnswer(invocation -> invocation.getArgument(0));

		MediaTrackResponse response = mediaTrackService.startWatching(trackId, coupleId, MediaStatus.WATCHING);

		assertThat(response.status()).isEqualTo(MediaStatus.WATCHING);
		assertThat(track.getStatus()).isEqualTo(MediaStatus.WATCHING);
		assertThat(track.getReviews()).isEmpty();
		assertThat(track.getWatchedDate()).isNull();
	}

	@Test
	void startWatchingThrowsWhenTrackIsNotWantToSee() {
		UUID trackId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		Couple couple = coupleWithMembers(UUID.randomUUID(), UUID.randomUUID());
		ReflectionTestUtils.setField(couple, "id", coupleId);
		MediaTrack track = new MediaTrack(couple, 603L, MediaType.MOVIE, MediaStatus.WATCHED);

		when(mediaTrackRepository.findById(trackId)).thenReturn(Optional.of(track));

		assertThatThrownBy(() -> mediaTrackService.startWatching(trackId, coupleId, MediaStatus.WATCHING))
			.isInstanceOf(IllegalArgumentException.class);
		verify(mediaTrackRepository, never()).save(any());
	}

	@Test
	void startWatchingThrowsWhenRequestedStatusIsNotWatching() {
		UUID trackId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();

		assertThatThrownBy(() -> mediaTrackService.startWatching(trackId, coupleId, MediaStatus.WATCHED))
			.isInstanceOf(IllegalArgumentException.class);
		verify(mediaTrackRepository, never()).findById(any());
	}

	@Test
	void startWatchingThrowsResourceNotFoundWhenTrackBelongsToAnotherCouple() {
		UUID trackId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		Couple otherCouple = coupleWithMembers(UUID.randomUUID(), UUID.randomUUID());
		ReflectionTestUtils.setField(otherCouple, "id", UUID.randomUUID());
		MediaTrack track = new MediaTrack(otherCouple, 603L, MediaType.MOVIE, MediaStatus.WANT_TO_SEE);

		when(mediaTrackRepository.findById(trackId)).thenReturn(Optional.of(track));

		assertThatThrownBy(() -> mediaTrackService.startWatching(trackId, coupleId, MediaStatus.WATCHING))
			.isInstanceOf(ResourceNotFoundException.class);
	}
}
