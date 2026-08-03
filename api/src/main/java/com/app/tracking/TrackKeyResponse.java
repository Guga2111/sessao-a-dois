package com.app.tracking;

import com.app.media.MediaType;

public record TrackKeyResponse(MediaType mediaType, Long tmdbId) {
}
