package com.app.couple;

public class CannotJoinOwnCoupleException extends RuntimeException {

	public CannotJoinOwnCoupleException() {
		super("usuario nao pode se vincular ao proprio casal");
	}
}
