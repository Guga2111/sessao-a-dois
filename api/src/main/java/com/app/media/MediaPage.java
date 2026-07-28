package com.app.media;

import java.util.List;

public record MediaPage(List<MediaSearchResult> results, int page, int totalResults, int totalPages) {

	static final int TMDB_MAX_PAGES = 500;

	static final int TMDB_MAX_RESULTS = TMDB_MAX_PAGES * 20;

	public MediaPage {
		if (totalPages > TMDB_MAX_PAGES) {
			totalPages = TMDB_MAX_PAGES;
		}
		if (totalResults > TMDB_MAX_RESULTS) {
			totalResults = TMDB_MAX_RESULTS;
		}
	}
}
