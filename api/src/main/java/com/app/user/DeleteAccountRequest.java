package com.app.user;

import jakarta.validation.constraints.NotBlank;

/**
 * Corpo de {@code DELETE /api/user/me} (epico 9, US-007). A senha e obrigatoria (E9.12): a
 * exclusao e irreversivel, e depender so do cookie de sessao significaria que um notebook
 * desbloqueado apaga a conta em dois cliques.
 */
public record DeleteAccountRequest(

	@NotBlank(message = "senha nao pode ser vazia")
	String password
) {
}
