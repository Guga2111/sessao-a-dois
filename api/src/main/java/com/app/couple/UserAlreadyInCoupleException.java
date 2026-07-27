package com.app.couple;

public class UserAlreadyInCoupleException extends RuntimeException {

	public UserAlreadyInCoupleException() {
		super("usuario ja pertence a um casal");
	}
}
