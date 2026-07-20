package com.app.couple;

public class CoupleAlreadyFullException extends RuntimeException {

	public CoupleAlreadyFullException() {
		super("casal ja esta completo");
	}
}
