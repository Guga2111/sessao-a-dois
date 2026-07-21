package com.app.tracking;

import com.app.couple.Couple;
import com.app.couple.CoupleRepository;
import com.app.media.MediaType;
import com.app.user.User;
import com.app.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

	private MediaTrackService mediaTrackService;

	@BeforeEach
	void setUp() {
		mediaTrackService = new MediaTrackService(mediaTrackRepository, coupleRepository, userRepository);
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
			603L, MediaType.MOVIE, MediaStatus.WATCHING, null, 136, 5, "Otimo filme");

		when(coupleRepository.findById(coupleId)).thenReturn(Optional.of(couple));
		when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(user));
		when(mediaTrackRepository.save(any(MediaTrack.class))).thenAnswer(invocation -> invocation.getArgument(0));

		MediaTrackResponse response = mediaTrackService.addTrack(coupleId, userId, request);

		assertThat(response.tmdbId()).isEqualTo(603L);
		assertThat(response.mediaType()).isEqualTo(MediaType.MOVIE);
		assertThat(response.status()).isEqualTo(MediaStatus.WATCHING);
		assertThat(response.runtime()).isEqualTo(136);
		assertThat(response.reviews()).hasSize(2);
		assertThat(response.reviews())
			.anySatisfy(review -> {
				assertThat(review.userId()).isEqualTo(userId);
				assertThat(review.rating()).isEqualTo(5);
				assertThat(review.opinion()).isEqualTo("Otimo filme");
			});
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
	void updateStatusThrowsResourceNotFoundWhenTrackBelongsToAnotherCouple() {
		UUID trackId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();
		UUID otherCoupleUser = UUID.randomUUID();
		Couple otherCouple = coupleWithMembers(otherCoupleUser, UUID.randomUUID());
		MediaTrack track = new MediaTrack(otherCouple, 603L, MediaType.MOVIE, MediaStatus.WATCHING);

		when(mediaTrackRepository.findById(trackId)).thenReturn(Optional.of(track));

		assertThatThrownBy(() -> mediaTrackService.updateStatus(trackId, coupleId, MediaStatus.WATCHED, LocalDate.now()))
			.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void updateStatusThrowsResourceNotFoundWhenTrackDoesNotExist() {
		UUID trackId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();

		when(mediaTrackRepository.findById(trackId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> mediaTrackService.updateStatus(trackId, coupleId, MediaStatus.WATCHED, LocalDate.now()))
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
}
