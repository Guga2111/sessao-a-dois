package com.app.tracking;

import com.app.couple.Couple;
import com.app.couple.CoupleRepository;
import com.app.media.MediaType;
import com.app.user.User;
import com.app.user.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class MediaTrackRepositoryTest {

	@Autowired
	private MediaTrackRepository mediaTrackRepository;

	@Autowired
	private CoupleRepository coupleRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private UserReviewRepository userReviewRepository;

	private Couple persistedCouple() {
		return coupleRepository.save(new Couple(UUID.randomUUID(), "MTR" + UUID.randomUUID().toString().substring(0, 4)));
	}

	private MediaTrack watchedTrack(Couple couple, MediaType mediaType, Integer runtime, LocalDate watchedDate,
			List<Integer> genreIds) {
		return trackWithStatus(couple, MediaStatus.WATCHED, mediaType, runtime, watchedDate, genreIds);
	}

	private MediaTrack trackWithStatus(Couple couple, MediaStatus status, MediaType mediaType, Integer runtime,
			LocalDate watchedDate, List<Integer> genreIds) {
		MediaTrack track = new MediaTrack(couple, System.nanoTime(), mediaType, status);
		track.setRuntime(runtime);
		track.setWatchedDate(watchedDate);
		track.setGenreIds(genreIds);
		return mediaTrackRepository.save(track);
	}

	@Test
	void sumRuntimeOnlyCountsWatchedMoviesOfTheCouple() {
		Couple couple = persistedCouple();
		Couple otherCouple = persistedCouple();
		watchedTrack(couple, MediaType.MOVIE, 120, LocalDate.now(), List.of());
		watchedTrack(couple, MediaType.MOVIE, 90, LocalDate.now(), List.of());
		watchedTrack(couple, MediaType.TV, 45, LocalDate.now(), List.of());
		watchedTrack(otherCouple, MediaType.MOVIE, 200, LocalDate.now(), List.of());

		MediaTrack watching = new MediaTrack(couple, System.nanoTime(), MediaType.MOVIE, MediaStatus.WATCHING);
		watching.setRuntime(999);
		mediaTrackRepository.save(watching);

		int total = mediaTrackRepository.sumRuntimeByCoupleIdAndStatusAndMediaType(
				couple.getId(), MediaStatus.WATCHED, MediaType.MOVIE);

		assertThat(total).isEqualTo(210);
	}

	@Test
	void sumRuntimeReturnsZeroWhenNoMatchingTracks() {
		Couple couple = persistedCouple();

		int total = mediaTrackRepository.sumRuntimeByCoupleIdAndStatusAndMediaType(
				couple.getId(), MediaStatus.WATCHED, MediaType.MOVIE);

		assertThat(total).isZero();
	}

	@Test
	void sumRuntimeForMonthOnlyCountsWatchedMoviesInThatMonthAndYear() {
		Couple couple = persistedCouple();
		int year = LocalDate.now().getYear();
		watchedTrack(couple, MediaType.MOVIE, 120, LocalDate.of(year, Month.JUNE, 10), List.of());
		watchedTrack(couple, MediaType.MOVIE, 90, LocalDate.of(year, Month.JUNE, 20), List.of());
		watchedTrack(couple, MediaType.MOVIE, 100, LocalDate.of(year, Month.JULY, 1), List.of());
		watchedTrack(couple, MediaType.MOVIE, 100, LocalDate.of(year - 1, Month.JUNE, 1), List.of());
		watchedTrack(couple, MediaType.TV, 45, LocalDate.of(year, Month.JUNE, 10), List.of());

		int total = mediaTrackRepository.sumRuntimeByCoupleIdAndStatusAndMediaTypeForMonth(
				couple.getId(), MediaStatus.WATCHED, MediaType.MOVIE, 6, year);

		assertThat(total).isEqualTo(210);
	}

	@Test
	void sumRuntimeForMonthReturnsZeroWhenNoMatchingTracks() {
		Couple couple = persistedCouple();
		int year = LocalDate.now().getYear();

		int total = mediaTrackRepository.sumRuntimeByCoupleIdAndStatusAndMediaTypeForMonth(
				couple.getId(), MediaStatus.WATCHED, MediaType.MOVIE, 6, year);

		assertThat(total).isZero();
	}

	@Test
	void countByCoupleIdAndStatusAndMediaTypeCountsMoviesAndSeriesSeparately() {
		Couple couple = persistedCouple();
		watchedTrack(couple, MediaType.MOVIE, 120, LocalDate.now(), List.of());
		watchedTrack(couple, MediaType.MOVIE, 90, LocalDate.now(), List.of());
		watchedTrack(couple, MediaType.TV, null, LocalDate.now(), List.of());

		assertThat(mediaTrackRepository.countByCoupleIdAndStatusAndMediaType(
				couple.getId(), MediaStatus.WATCHED, MediaType.MOVIE)).isEqualTo(2);
		assertThat(mediaTrackRepository.countByCoupleIdAndStatusAndMediaType(
				couple.getId(), MediaStatus.WATCHED, MediaType.TV)).isEqualTo(1);
	}

	@Test
	void countByCoupleIdAndStatusGroupedByMonthGroupsWithinCurrentYear() {
		Couple couple = persistedCouple();
		int year = LocalDate.now().getYear();
		watchedTrack(couple, MediaType.MOVIE, 100, LocalDate.of(year, Month.JANUARY, 10), List.of());
		watchedTrack(couple, MediaType.MOVIE, 100, LocalDate.of(year, Month.JANUARY, 20), List.of());
		watchedTrack(couple, MediaType.TV, null, LocalDate.of(year, Month.MARCH, 5), List.of());
		watchedTrack(couple, MediaType.MOVIE, 100, LocalDate.of(year - 1, Month.JANUARY, 1), List.of());

		List<MediaTrackRepository.MonthlyCount> counts = mediaTrackRepository
				.countByCoupleIdAndStatusGroupedByMonth(couple.getId(), MediaStatus.WATCHED, year);

		assertThat(counts).hasSize(2);
		assertThat(counts).anySatisfy(c -> {
			assertThat(c.getMonth()).isEqualTo(1);
			assertThat(c.getTotal()).isEqualTo(2L);
		});
		assertThat(counts).anySatisfy(c -> {
			assertThat(c.getMonth()).isEqualTo(3);
			assertThat(c.getTotal()).isEqualTo(1L);
		});
	}

	@Test
	void countGenreOccurrencesByCoupleIdAndStatusCountsEachGenreAcrossTracks() {
		Couple couple = persistedCouple();
		watchedTrack(couple, MediaType.MOVIE, 100, LocalDate.now(), List.of(28, 12));
		watchedTrack(couple, MediaType.MOVIE, 100, LocalDate.now(), List.of(28));
		watchedTrack(couple, MediaType.TV, null, LocalDate.now(), List.of(12, 18));

		List<MediaTrackRepository.GenreCount> counts = mediaTrackRepository
				.countGenreOccurrencesByCoupleIdAndStatus(couple.getId(), MediaStatus.WATCHED);

		assertThat(counts).hasSize(3);
		assertThat(counts)
				.filteredOn(c -> c.getGenreId().equals(28))
				.singleElement()
				.satisfies(c -> assertThat(c.getTotal()).isEqualTo(2L));
		assertThat(counts)
				.filteredOn(c -> c.getGenreId().equals(12))
				.singleElement()
				.satisfies(c -> assertThat(c.getTotal()).isEqualTo(2L));
		assertThat(counts)
				.filteredOn(c -> c.getGenreId().equals(18))
				.singleElement()
				.satisfies(c -> assertThat(c.getTotal()).isEqualTo(1L));
	}

	@Test
	void sumRuntimeIgnoresNullRuntimesButStillSumsTheRest() {
		Couple couple = persistedCouple();
		watchedTrack(couple, MediaType.MOVIE, 120, LocalDate.now(), List.of());
		watchedTrack(couple, MediaType.MOVIE, null, LocalDate.now(), List.of());
		watchedTrack(couple, MediaType.MOVIE, 30, LocalDate.now(), List.of());

		int total = mediaTrackRepository.sumRuntimeByCoupleIdAndStatusAndMediaType(
				couple.getId(), MediaStatus.WATCHED, MediaType.MOVIE);

		assertThat(total).isEqualTo(150);
	}

	@Test
	void countGenreOccurrencesExcludesNonWatchedTracks() {
		Couple couple = persistedCouple();
		watchedTrack(couple, MediaType.MOVIE, 100, LocalDate.now(), List.of(28));
		trackWithStatus(couple, MediaStatus.WANT_TO_SEE, MediaType.MOVIE, 100, LocalDate.now(), List.of(28, 12));
		trackWithStatus(couple, MediaStatus.WATCHING, MediaType.TV, null, LocalDate.now(), List.of(18));

		List<MediaTrackRepository.GenreCount> counts = mediaTrackRepository
				.countGenreOccurrencesByCoupleIdAndStatus(couple.getId(), MediaStatus.WATCHED);

		assertThat(counts).singleElement().satisfies(c -> {
			assertThat(c.getGenreId()).isEqualTo(28);
			assertThat(c.getTotal()).isEqualTo(1L);
		});
	}

	@Test
	void countByCoupleIdAndStatusGroupedByMonthExcludesOtherStatuses() {
		Couple couple = persistedCouple();
		int year = LocalDate.now().getYear();
		watchedTrack(couple, MediaType.MOVIE, 100, LocalDate.of(year, Month.JANUARY, 10), List.of());
		trackWithStatus(couple, MediaStatus.WANT_TO_SEE, MediaType.MOVIE, 100, LocalDate.of(year, Month.JANUARY, 15),
				List.of());
		trackWithStatus(couple, MediaStatus.WATCHING, MediaType.MOVIE, 100, LocalDate.of(year, Month.JANUARY, 20),
				List.of());

		List<MediaTrackRepository.MonthlyCount> counts = mediaTrackRepository
				.countByCoupleIdAndStatusGroupedByMonth(couple.getId(), MediaStatus.WATCHED, year);

		assertThat(counts).singleElement().satisfies(c -> {
			assertThat(c.getMonth()).isEqualTo(1);
			assertThat(c.getTotal()).isEqualTo(1L);
		});
	}

	@Test
	void queriesAreScopedByCoupleIdAndNeverLeakOtherCouplesData() {
		Couple couple = persistedCouple();
		Couple otherCouple = persistedCouple();
		watchedTrack(otherCouple, MediaType.MOVIE, 500, LocalDate.now(), List.of(99));

		assertThat(mediaTrackRepository.sumRuntimeByCoupleIdAndStatusAndMediaType(
				couple.getId(), MediaStatus.WATCHED, MediaType.MOVIE)).isZero();
		assertThat(mediaTrackRepository.countByCoupleIdAndStatusAndMediaType(
				couple.getId(), MediaStatus.WATCHED, MediaType.MOVIE)).isZero();
		assertThat(mediaTrackRepository.countByCoupleIdAndStatusGroupedByMonth(
				couple.getId(), MediaStatus.WATCHED, LocalDate.now().getYear())).isEmpty();
		assertThat(mediaTrackRepository.countGenreOccurrencesByCoupleIdAndStatus(
				couple.getId(), MediaStatus.WATCHED)).isEmpty();
	}

	@Test
	void findByCoupleIdAndStatusOrderByCreatedAtDescReturnsFirstPageOrderedNewestFirst() throws InterruptedException {
		Couple couple = persistedCouple();
		MediaTrack first = trackWithStatus(couple, MediaStatus.WANT_TO_SEE, MediaType.MOVIE, null, null, List.of());
		mediaTrackRepository.flush();
		Thread.sleep(5);
		MediaTrack second = trackWithStatus(couple, MediaStatus.WANT_TO_SEE, MediaType.MOVIE, null, null, List.of());
		mediaTrackRepository.flush();
		Thread.sleep(5);
		MediaTrack third = trackWithStatus(couple, MediaStatus.WANT_TO_SEE, MediaType.MOVIE, null, null, List.of());
		mediaTrackRepository.flush();

		Page<MediaTrack> page = mediaTrackRepository.findByCoupleIdAndStatusOrderByCreatedAtDesc(
				couple.getId(), MediaStatus.WANT_TO_SEE, PageRequest.of(0, 2));

		assertThat(page.getTotalElements()).isEqualTo(3);
		assertThat(page.getTotalPages()).isEqualTo(2);
		assertThat(page.getContent()).extracting(MediaTrack::getId)
				.containsExactly(third.getId(), second.getId());
	}

	@Test
	void findByCoupleIdAndStatusOrderByCreatedAtDescReturnsSecondPage() throws InterruptedException {
		Couple couple = persistedCouple();
		MediaTrack first = trackWithStatus(couple, MediaStatus.WANT_TO_SEE, MediaType.MOVIE, null, null, List.of());
		mediaTrackRepository.flush();
		Thread.sleep(5);
		MediaTrack second = trackWithStatus(couple, MediaStatus.WANT_TO_SEE, MediaType.MOVIE, null, null, List.of());
		mediaTrackRepository.flush();
		Thread.sleep(5);
		trackWithStatus(couple, MediaStatus.WANT_TO_SEE, MediaType.MOVIE, null, null, List.of());
		mediaTrackRepository.flush();

		Page<MediaTrack> page = mediaTrackRepository.findByCoupleIdAndStatusOrderByCreatedAtDesc(
				couple.getId(), MediaStatus.WANT_TO_SEE, PageRequest.of(1, 2));

		assertThat(page.getTotalElements()).isEqualTo(3);
		assertThat(page.getContent()).extracting(MediaTrack::getId).containsExactly(first.getId());
	}

	@Test
	void findByCoupleIdAndStatusOrderByCreatedAtDescIsScopedByStatusAndCouple() {
		Couple couple = persistedCouple();
		Couple otherCouple = persistedCouple();
		trackWithStatus(couple, MediaStatus.WATCHING, MediaType.MOVIE, null, null, List.of());
		trackWithStatus(couple, MediaStatus.WANT_TO_SEE, MediaType.MOVIE, null, null, List.of());
		trackWithStatus(otherCouple, MediaStatus.WANT_TO_SEE, MediaType.MOVIE, null, null, List.of());

		Page<MediaTrack> page = mediaTrackRepository.findByCoupleIdAndStatusOrderByCreatedAtDesc(
				couple.getId(), MediaStatus.WANT_TO_SEE, PageRequest.of(0, 20));

		assertThat(page.getTotalElements()).isEqualTo(1);
	}

	@Test
	void deletingTrackCascadesReviewsOfBothMembersWithoutLeavingOrphanRows() {
		Couple couple = persistedCouple();
		User user1 = userRepository.save(new User("Ana", "ana-" + UUID.randomUUID() + "@example.com", "hash"));
		User user2 = userRepository.save(new User("Bob", "bob-" + UUID.randomUUID() + "@example.com", "hash"));

		MediaTrack track = trackWithStatus(couple, MediaStatus.WATCHED, MediaType.MOVIE, 120, LocalDate.now(),
				List.of());
		track.getReviews().add(new UserReview(track, user1, 5, "Adorei"));
		track.getReviews().add(new UserReview(track, user2, 3, "Ok"));
		track = mediaTrackRepository.save(track);
		UUID trackId = track.getId();

		assertThat(userReviewRepository.findByMediaTrackIdAndUserId(trackId, user1.getId())).isPresent();
		assertThat(userReviewRepository.findByMediaTrackIdAndUserId(trackId, user2.getId())).isPresent();

		mediaTrackRepository.delete(track);
		mediaTrackRepository.flush();

		assertThat(mediaTrackRepository.findById(trackId)).isEmpty();
		assertThat(userReviewRepository.findByMediaTrackIdAndUserId(trackId, user1.getId())).isEmpty();
		assertThat(userReviewRepository.findByMediaTrackIdAndUserId(trackId, user2.getId())).isEmpty();
	}
}
