package com.app.auth;

public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException() {
		super("credenciais invalidas");
	}
}
