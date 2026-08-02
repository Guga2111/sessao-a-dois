package com.app.media;

import org.springframework.util.StringUtils;

public enum DiscoverSortBy {

	POPULARITY_DESC("popularity.desc"),
	POPULARITY_ASC("popularity.asc"),
	VOTE_AVERAGE_DESC("vote_average.desc"),
	VOTE_AVERAGE_ASC("vote_average.asc"),
	RELEASE_DATE_DESC("release_date.desc"),
	RELEASE_DATE_ASC("release_date.asc");

	private final String wireValue;

	DiscoverSortBy(String wireValue) {
		this.wireValue = wireValue;
	}

	public boolean descending() {
		return this == POPULARITY_DESC || this == VOTE_AVERAGE_DESC || this == RELEASE_DATE_DESC;
	}

	/**
	 * TMDB's sort_by has no single "release_date" field: movies are sorted by
	 * primary_release_date, series by first_air_date.
	 */
	public String tmdbValue(MediaType mediaType) {
		if (this == RELEASE_DATE_DESC) {
			return mediaType == MediaType.TV ? "first_air_date.desc" : "primary_release_date.desc";
		}
		if (this == RELEASE_DATE_ASC) {
			return mediaType == MediaType.TV ? "first_air_date.asc" : "primary_release_date.asc";
		}
		return wireValue;
	}

	public static DiscoverSortBy fromValueOrDefault(String value) {
		if (!StringUtils.hasText(value)) {
			return POPULARITY_DESC;
		}

		for (DiscoverSortBy sortBy : values()) {
			if (sortBy.wireValue.equalsIgnoreCase(value)) {
				return sortBy;
			}
		}

		throw new InvalidSortByException(value);
	}
}
