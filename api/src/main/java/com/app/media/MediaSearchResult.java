package com.app.media;

public record MediaSearchResult(
		long tmdbId,
		MediaType mediaType,
		String title,
		Integer year,
		String posterUrl,
		String overview,
		Double voteAverage) {
}
