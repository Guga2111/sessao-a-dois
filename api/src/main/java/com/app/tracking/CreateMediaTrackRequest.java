package com.app.tracking;

import com.app.media.MediaType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateMediaTrackRequest(

	@NotNull(message = "tmdbId nao pode ser vazio")
	Long tmdbId,

	@NotNull(message = "mediaType nao pode ser vazio")
	MediaType mediaType,

	@NotNull(message = "status nao pode ser vazio")
	MediaStatus status,

	LocalDate watchedDate,

	Integer runtime,

	@Min(value = 1, message = "rating deve ser entre 1 e 5")
	@Max(value = 5, message = "rating deve ser entre 1 e 5")
	Integer rating,

	String opinion
) {
}
