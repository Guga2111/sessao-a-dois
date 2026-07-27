package com.app.auth;

public class EmailAlreadyExistsException extends RuntimeException {

	public EmailAlreadyExistsException() {
		super("e-mail ja cadastrado");
	}
}
