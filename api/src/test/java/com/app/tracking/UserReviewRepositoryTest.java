package com.app.tracking;

import com.app.couple.Couple;
import com.app.couple.CoupleRepository;
import com.app.media.MediaType;
import com.app.user.User;
import com.app.user.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest
class UserReviewRepositoryTest {

	@Autowired
	private UserReviewRepository userReviewRepository;

	@Autowired
	private MediaTrackRepository mediaTrackRepository;

	@Autowired
	private CoupleRepository coupleRepository;

	@Autowired
	private UserRepository userRepository;

	private Couple persistedCouple() {
		return coupleRepository.save(new Couple(UUID.randomUUID(), "URR" + UUID.randomUUID().toString().substring(0, 4)));
	}

	private User persistedUser() {
		return userRepository.save(new User("Test User", UUID.randomUUID() + "@example.com", "hash"));
	}

	private MediaTrack persistedTrack(Couple couple, MediaStatus status) {
		MediaTrack track = new MediaTrack(couple, System.nanoTime(), MediaType.MOVIE, status);
		return mediaTrackRepository.save(track);
	}

	@Test
	void averageRatingConsidersOnlyWatchedTracksOfTheCouple() {
		Couple couple = persistedCouple();
		MediaTrack watchedOne = persistedTrack(couple, MediaStatus.WATCHED);
		MediaTrack watchedTwo = persistedTrack(couple, MediaStatus.WATCHED);
		MediaTrack watching = persistedTrack(couple, MediaStatus.WATCHING);

		userReviewRepository.save(new UserReview(watchedOne, persistedUser(), 4, "bom"));
		userReviewRepository.save(new UserReview(watchedTwo, persistedUser(), 2, "ruim"));
		userReviewRepository.save(new UserReview(watching, persistedUser(), 5, "ainda assistindo"));

		Double average = userReviewRepository.findAverageRatingByCoupleIdAndMediaTrackStatus(
				couple.getId(), MediaStatus.WATCHED);

		assertThat(average).isCloseTo(3.0, within(0.001));
	}

	@Test
	void averageRatingIsNullWhenCoupleHasNoWatchedReviews() {
		Couple couple = persistedCouple();

		Double average = userReviewRepository.findAverageRatingByCoupleIdAndMediaTrackStatus(
				couple.getId(), MediaStatus.WATCHED);

		assertThat(average).isNull();
	}

	@Test
	void averageRatingDoesNotLeakOtherCouplesReviews() {
		Couple couple = persistedCouple();
		Couple otherCouple = persistedCouple();
		MediaTrack otherWatched = persistedTrack(otherCouple, MediaStatus.WATCHED);
		userReviewRepository.save(new UserReview(otherWatched, persistedUser(), 5, "otimo"));

		Double average = userReviewRepository.findAverageRatingByCoupleIdAndMediaTrackStatus(
				couple.getId(), MediaStatus.WATCHED);

		assertThat(average).isNull();
	}
}
