package com.app.couple;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Gera codigos de convite alfanumericos de comprimento fixo, evitando
 * caracteres ambiguos (0/O, 1/l/I).
 */
@Component
public class InviteCodeGenerator {

	private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
	private static final int LENGTH = 8;

	private final SecureRandom random = new SecureRandom();

	public String generate() {
		StringBuilder code = new StringBuilder(LENGTH);
		for (int i = 0; i < LENGTH; i++) {
			code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
		}
		return code.toString();
	}
}
