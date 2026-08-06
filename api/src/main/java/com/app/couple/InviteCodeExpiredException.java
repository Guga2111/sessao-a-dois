package com.app.couple;

public class InviteCodeExpiredException extends RuntimeException {

	public InviteCodeExpiredException() {
		super("codigo de convite expirado");
	}
}
