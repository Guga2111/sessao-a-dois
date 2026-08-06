package com.app.auth;

/** Lancada quando um refresh token ja revogado e apresentado fora da janela de graca (decisao E4). */
public class RefreshReuseDetectedException extends RuntimeException {

	public RefreshReuseDetectedException() {
		super("reuso de refresh token detectado");
	}
}
