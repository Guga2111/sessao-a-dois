package com.app.media;

public class InvalidMediaTypeException extends RuntimeException {

	public InvalidMediaTypeException(String mediaType) {
		super("Tipo de midia invalido: '" + mediaType + "'. Use 'movie' ou 'tv'.");
	}
}
