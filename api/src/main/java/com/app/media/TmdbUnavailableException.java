package com.app.media;

public class TmdbUnavailableException extends RuntimeException {

	public TmdbUnavailableException(String endpoint, Throwable cause) {
		super("Falha ao chamar o TMDB em " + endpoint, cause);
	}
}
