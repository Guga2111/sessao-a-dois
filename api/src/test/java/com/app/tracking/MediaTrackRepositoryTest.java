package com.app.tracking;

import com.app.couple.Couple;
import com.app.couple.CoupleRepository;
import com.app.media.MediaType;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

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

	private Couple persistedCouple() {
		return coupleRepository.save(new Couple(UUID.randomUUID(), "MTR" + UUID.randomUUID().toString().substring(0, 4)));
	}

	private MediaTrack watchedTrack(Couple couple, MediaType mediaType, Integer runtime, LocalDate watchedDate,
			List<Integer> genreIds) {
		MediaTrack track = new MediaTrack(couple, System.nanoTime(), mediaType, MediaStatus.WATCHED);
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
}
