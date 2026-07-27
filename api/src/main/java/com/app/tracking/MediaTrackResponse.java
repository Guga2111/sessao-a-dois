package com.app.tracking;

import com.app.media.MediaType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record MediaTrackResponse(
	UUID id,
	Long tmdbId,
	MediaType mediaType,
	MediaStatus status,
	LocalDate watchedDate,
	Integer runtime,
	LocalDateTime createdAt,
	List<ReviewDto> reviews
) {
}
