package com.app.media;

import java.util.List;

public record MediaDetails(
		long tmdbId,
		MediaType mediaType,
		String title,
		Integer year,
		String posterUrl,
		String overview,
		List<String> genres,
		List<Integer> genreIds,
		Double voteAverage,
		Integer runtime,
		List<WatchProvider> watchProviders) {
}
