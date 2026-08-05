package com.app.couple;

public class NotCoupleCreatorException extends RuntimeException {

	public NotCoupleCreatorException() {
		super("apenas quem criou o casal pode regenerar o codigo de convite");
	}
}
