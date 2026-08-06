package com.app.match;

import com.app.media.MediaType;

import jakarta.validation.constraints.NotNull;

public record LikeRequest(

	@NotNull(message = "tmdbId nao pode ser vazio")
	Long tmdbId,

	@NotNull(message = "mediaType nao pode ser vazio")
	MediaType mediaType,

	String title,

	String posterUrl,

	Integer releaseYear
) {

	public LikeRequest(Long tmdbId, MediaType mediaType) {
		this(tmdbId, mediaType, null, null, null);
	}
}
