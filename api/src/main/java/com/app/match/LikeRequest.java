package com.app.match;

import com.app.media.MediaType;

import jakarta.validation.constraints.NotNull;

public record LikeRequest(

	@NotNull(message = "tmdbId nao pode ser vazio")
	Long tmdbId,

	@NotNull(message = "mediaType nao pode ser vazio")
	MediaType mediaType
) {
}
