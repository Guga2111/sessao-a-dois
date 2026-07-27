package com.app.couple;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Gera codigos de convite alfanumericos (6 a 8 caracteres), evitando
 * caracteres ambiguos (0/O, 1/l/I).
 */
@Component
public class InviteCodeGenerator {

	private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
	private static final int MIN_LENGTH = 6;
	private static final int MAX_LENGTH = 8;

	private final SecureRandom random = new SecureRandom();

	public String generate() {
		int length = MIN_LENGTH + random.nextInt(MAX_LENGTH - MIN_LENGTH + 1);
		StringBuilder code = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
		}
		return code.toString();
	}
}
