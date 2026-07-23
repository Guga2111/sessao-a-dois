package com.app.match;

import com.app.media.MediaType;

public record PendingMatchDto(long tmdbId, MediaType mediaType, String title, String posterUrl) {
}
