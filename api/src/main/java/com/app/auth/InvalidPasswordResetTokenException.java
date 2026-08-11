package com.app.auth;

/**
 * Token de reset inexistente, expirado, ja usado ou adulterado (US-007) - as quatro
 * situacoes respondem com a MESMA mensagem generica, sem distinguir os casos, para nao
 * virar oraculo sobre o estado do token.
 */
public class InvalidPasswordResetTokenException extends RuntimeException {

	public InvalidPasswordResetTokenException() {
		super("token invalido ou expirado");
	}
}
