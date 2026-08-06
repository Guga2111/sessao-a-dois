package com.app.couple;

public class CoupleNotFoundException extends RuntimeException {

	public CoupleNotFoundException() {
		super("usuario nao pertence a nenhum casal");
	}
}
