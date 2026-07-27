package com.app.media;

public class MediaNotFoundException extends RuntimeException {

	public MediaNotFoundException(MediaType mediaType, long tmdbId) {
		super("Titulo nao encontrado: " + mediaType.tmdbPath() + "/" + tmdbId);
	}
}
