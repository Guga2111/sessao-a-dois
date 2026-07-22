package com.app.tracking;

import java.util.List;

public record StatsResponse(
	int totalWatchedHours,
	int totalWatchedMinutes,
	int currentMonthWatchedHours,
	long movieCount,
	long tvCount,
	double moviePercentage,
	double tvPercentage,
	long totalTitles,
	double averageRating,
	String favoriteGenre,
	List<GenreStat> topGenres,
	List<MonthlyStat> monthlySeries
) {
}
