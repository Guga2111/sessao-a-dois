package com.app.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code PUT /api/auth/password} (epico 9, US-006). A politica da senha nova e
 * exatamente a mesma do cadastro (E9.9): a anotacao {@code @Size(min = 8, max = 72)} do
 * {@link RegisterRequest} E a politica - nao existe validador dedicado, e nao deve nascer um.
 */
public record ChangePasswordRequest(

	@NotBlank(message = "senha atual nao pode ser vazia")
	String currentPassword,

	@NotBlank(message = "senha nao pode ser vazia")
	@Size(min = 8, max = 72, message = "senha deve ter entre 8 e 72 caracteres")
	String newPassword
) {
}
