package com.app.match;

import com.app.media.MediaType;

public record MatchEvent(long tmdbId, String title, MediaType mediaType) {
}
