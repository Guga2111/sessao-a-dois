package com.app.tracking;

import com.app.media.MediaType;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StatsService {

	private final MediaTrackRepository mediaTrackRepository;
	private final UserReviewRepository userReviewRepository;

	public StatsService(MediaTrackRepository mediaTrackRepository, UserReviewRepository userReviewRepository) {
		this.mediaTrackRepository = mediaTrackRepository;
		this.userReviewRepository = userReviewRepository;
	}

	public StatsResponse emptyStats() {
		return new StatsResponse(0, 0, 0, 0, 0, 0.0, 0.0, 0, 0.0, null, List.of(), buildMonthlySeries(List.of()));
	}

	public StatsResponse getStats(UUID coupleId) {
		LocalDate today = LocalDate.now();

		int totalMovieMinutes = mediaTrackRepository.sumRuntimeByCoupleIdAndStatusAndMediaType(
			coupleId, MediaStatus.WATCHED, MediaType.MOVIE);
		int currentMonthMovieMinutes = mediaTrackRepository.sumRuntimeByCoupleIdAndStatusAndMediaTypeForMonth(
			coupleId, MediaStatus.WATCHED, MediaType.MOVIE, today.getMonthValue(), today.getYear());

		long movieCount = mediaTrackRepository.countByCoupleIdAndStatusAndMediaType(
			coupleId, MediaStatus.WATCHED, MediaType.MOVIE);
		long tvCount = mediaTrackRepository.countByCoupleIdAndStatusAndMediaType(
			coupleId, MediaStatus.WATCHED, MediaType.TV);
		long totalTitles = movieCount + tvCount;

		Double averageRating = userReviewRepository.findAverageRatingByCoupleIdAndMediaTrackStatus(
			coupleId, MediaStatus.WATCHED);

		List<GenreStat> topGenres = buildTopGenres(
			mediaTrackRepository.countGenreOccurrencesByCoupleIdAndStatus(coupleId, MediaStatus.WATCHED));
		String favoriteGenre = topGenres.isEmpty() ? null : topGenres.get(0).name();

		List<MonthlyStat> monthlySeries = buildMonthlySeries(
			mediaTrackRepository.countByCoupleIdAndStatusGroupedByMonth(coupleId, MediaStatus.WATCHED, today.getYear()));

		return new StatsResponse(
			totalMovieMinutes / 60,
			totalMovieMinutes % 60,
			currentMonthMovieMinutes / 60,
			movieCount,
			tvCount,
			percentage(movieCount, totalTitles),
			percentage(tvCount, totalTitles),
			totalTitles,
			averageRating == null ? 0.0 : round1(averageRating),
			favoriteGenre,
			topGenres,
			monthlySeries
		);
	}

	private List<GenreStat> buildTopGenres(List<MediaTrackRepository.GenreCount> genreCounts) {
		List<Map.Entry<String, Long>> named = genreCounts.stream()
			.filter(gc -> GenreNames.nameFor(gc.getGenreId()) != null)
			.map(gc -> Map.entry(GenreNames.nameFor(gc.getGenreId()), gc.getTotal()))
			.toList();

		long total = named.stream().mapToLong(Map.Entry::getValue).sum();

		return named.stream()
			.sorted(Comparator.comparingLong((Map.Entry<String, Long> entry) -> entry.getValue()).reversed())
			.limit(5)
			.map(entry -> new GenreStat(entry.getKey(), entry.getValue(), percentage(entry.getValue(), total)))
			.toList();
	}

	private List<MonthlyStat> buildMonthlySeries(List<MediaTrackRepository.MonthlyCount> monthlyCounts) {
		Map<Integer, Long> countsByMonth = monthlyCounts.stream()
			.collect(Collectors.toMap(MediaTrackRepository.MonthlyCount::getMonth,
				MediaTrackRepository.MonthlyCount::getTotal));

		List<MonthlyStat> series = new ArrayList<>(12);
		for (int month = 1; month <= 12; month++) {
			series.add(new MonthlyStat(month, countsByMonth.getOrDefault(month, 0L)));
		}
		return series;
	}

	private double percentage(long part, long total) {
		return total == 0 ? 0.0 : round1(part * 100.0 / total);
	}

	private double round1(double value) {
		return Math.round(value * 10.0) / 10.0;
	}
}
