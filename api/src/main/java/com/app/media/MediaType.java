package com.app.media;

import org.springframework.util.StringUtils;

public enum MediaType {
	MOVIE("movie"),
	TV("tv");

	private final String tmdbPath;

	MediaType(String tmdbPath) {
		this.tmdbPath = tmdbPath;
	}

	public String tmdbPath() {
		return tmdbPath;
	}

	public static MediaType fromPathValue(String value) {
		for (MediaType mediaType : values()) {
			if (mediaType.tmdbPath.equalsIgnoreCase(value)) {
				return mediaType;
			}
		}

		throw new InvalidMediaTypeException(value);
	}

	/**
	 * Like {@link #fromPathValue(String)}, but a blank/absent value means "no
	 * restriction" (used by GET /api/media/discover, where omitting mediaType
	 * means both movie and tv).
	 */
	public static MediaType fromQueryValueOrNull(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}

		return fromPathValue(value);
	}
}
