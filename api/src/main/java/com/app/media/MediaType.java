package com.app.media;

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
}
