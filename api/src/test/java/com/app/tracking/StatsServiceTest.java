package com.app.tracking;

import com.app.media.MediaType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

	@Mock
	private MediaTrackRepository mediaTrackRepository;

	@Mock
	private UserReviewRepository userReviewRepository;

	private StatsService statsService;

	@BeforeEach
	void setUp() {
		statsService = new StatsService(mediaTrackRepository, userReviewRepository);
	}

	@Test
	void getStatsCombinesQueriesIntoDto() {
		UUID coupleId = UUID.randomUUID();
		int currentYear = LocalDate.now().getYear();
		int currentMonth = LocalDate.now().getMonthValue();

		when(mediaTrackRepository.sumRuntimeByCoupleIdAndStatusAndMediaType(
			coupleId, MediaStatus.WATCHED, MediaType.MOVIE)).thenReturn(150);
		when(mediaTrackRepository.sumRuntimeByCoupleIdAndStatusAndMediaTypeForMonth(
			eq(coupleId), eq(MediaStatus.WATCHED), eq(MediaType.MOVIE), eq(currentMonth), eq(currentYear)))
			.thenReturn(90);
		when(mediaTrackRepository.countByCoupleIdAndStatusAndMediaType(
			coupleId, MediaStatus.WATCHED, MediaType.MOVIE)).thenReturn(3L);
		when(mediaTrackRepository.countByCoupleIdAndStatusAndMediaType(
			coupleId, MediaStatus.WATCHED, MediaType.TV)).thenReturn(1L);
		when(userReviewRepository.findAverageRatingByCoupleIdAndMediaTrackStatus(coupleId, MediaStatus.WATCHED))
			.thenReturn(4.25);
		when(mediaTrackRepository.countGenreOccurrencesByCoupleIdAndStatus(coupleId, MediaStatus.WATCHED))
			.thenReturn(List.of(
				genreCount(28, 3L),
				genreCount(12, 1L),
				genreCount(999999, 5L)
			));
		when(mediaTrackRepository.countByCoupleIdAndStatusGroupedByMonth(eq(coupleId), eq(MediaStatus.WATCHED), anyInt()))
			.thenReturn(List.of(monthlyCount(1, 2L), monthlyCount(3, 1L)));

		StatsResponse stats = statsService.getStats(coupleId);

		assertThat(stats.totalWatchedHours()).isEqualTo(2);
		assertThat(stats.totalWatchedMinutes()).isEqualTo(30);
		assertThat(stats.currentMonthWatchedHours()).isEqualTo(1);
		assertThat(stats.movieCount()).isEqualTo(3);
		assertThat(stats.tvCount()).isEqualTo(1);
		assertThat(stats.totalTitles()).isEqualTo(4);
		assertThat(stats.moviePercentage()).isEqualTo(75.0);
		assertThat(stats.tvPercentage()).isEqualTo(25.0);
		assertThat(stats.averageRating()).isEqualTo(4.3);
		assertThat(stats.favoriteGenre()).isEqualTo("Ação");
		assertThat(stats.topGenres()).hasSize(2);
		assertThat(stats.topGenres().get(0).name()).isEqualTo("Ação");
		assertThat(stats.topGenres().get(0).percentage()).isEqualTo(75.0);
		assertThat(stats.topGenres().get(1).name()).isEqualTo("Aventura");
		assertThat(stats.monthlySeries()).hasSize(12);
		assertThat(stats.monthlySeries().get(0).count()).isEqualTo(2L);
		assertThat(stats.monthlySeries().get(2).count()).isEqualTo(1L);
		assertThat(stats.monthlySeries().get(1).count()).isZero();
	}

	@Test
	void getStatsReturnsEmptyStateWhenCoupleHasNoWatchedTitles() {
		UUID coupleId = UUID.randomUUID();

		when(mediaTrackRepository.sumRuntimeByCoupleIdAndStatusAndMediaType(any(), any(), any())).thenReturn(0);
		when(mediaTrackRepository.sumRuntimeByCoupleIdAndStatusAndMediaTypeForMonth(any(), any(), any(), anyInt(), anyInt()))
			.thenReturn(0);
		when(mediaTrackRepository.countByCoupleIdAndStatusAndMediaType(any(), any(), any())).thenReturn(0L);
		when(userReviewRepository.findAverageRatingByCoupleIdAndMediaTrackStatus(any(), any())).thenReturn(null);
		when(mediaTrackRepository.countGenreOccurrencesByCoupleIdAndStatus(any(), any())).thenReturn(List.of());
		when(mediaTrackRepository.countByCoupleIdAndStatusGroupedByMonth(any(), any(), anyInt())).thenReturn(List.of());

		StatsResponse stats = statsService.getStats(coupleId);

		assertThat(stats.totalWatchedHours()).isZero();
		assertThat(stats.totalWatchedMinutes()).isZero();
		assertThat(stats.currentMonthWatchedHours()).isZero();
		assertThat(stats.movieCount()).isZero();
		assertThat(stats.tvCount()).isZero();
		assertThat(stats.totalTitles()).isZero();
		assertThat(stats.moviePercentage()).isZero();
		assertThat(stats.tvPercentage()).isZero();
		assertThat(stats.averageRating()).isZero();
		assertThat(stats.favoriteGenre()).isNull();
		assertThat(stats.topGenres()).isEmpty();
		assertThat(stats.monthlySeries()).hasSize(12);
		assertThat(stats.monthlySeries()).allSatisfy(monthly -> assertThat(monthly.count()).isZero());
	}

	private MediaTrackRepository.GenreCount genreCount(int genreId, long total) {
		return new MediaTrackRepository.GenreCount() {
			@Override
			public Integer getGenreId() {
				return genreId;
			}

			@Override
			public Long getTotal() {
				return total;
			}
		};
	}

	private MediaTrackRepository.MonthlyCount monthlyCount(int month, long total) {
		return new MediaTrackRepository.MonthlyCount() {
			@Override
			public Integer getMonth() {
				return month;
			}

			@Override
			public Long getTotal() {
				return total;
			}
		};
	}
}
