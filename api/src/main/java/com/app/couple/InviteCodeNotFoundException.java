package com.app.couple;

public class InviteCodeNotFoundException extends RuntimeException {

	public InviteCodeNotFoundException() {
		super("codigo de convite nao encontrado");
	}
}
