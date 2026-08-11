package com.app.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /api/auth/reset-password} (US-007). A politica da senha nova e
 * exatamente a mesma do cadastro/troca autenticada (mesma anotacao e mesma mensagem de
 * {@link RegisterRequest}/{@link ChangePasswordRequest}) - nao existe validador dedicado
 * de senha neste projeto.
 */
public record ResetPasswordRequest(

	@NotBlank(message = "token nao pode ser vazio")
	String token,

	@NotBlank(message = "senha nao pode ser vazia")
	@Size(min = 8, max = 72, message = "senha deve ter entre 8 e 72 caracteres")
	String newPassword
) {
}
