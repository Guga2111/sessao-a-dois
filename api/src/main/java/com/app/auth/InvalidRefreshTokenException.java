package com.app.auth;

/** Cobre refresh token ausente, inexistente, malformado ou expirado (sem ter sido revogado). */
public class InvalidRefreshTokenException extends RuntimeException {

	public InvalidRefreshTokenException() {
		super("refresh token invalido");
	}
}
