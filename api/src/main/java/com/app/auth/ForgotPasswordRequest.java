package com.app.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Corpo de {@code POST /api/auth/forgot-password} (US-006). O endpoint responde 202
 * tanto para e-mail existente quanto inexistente - so a validacao de formato (aqui)
 * pode virar 400.
 */
public record ForgotPasswordRequest(

	@NotBlank(message = "e-mail nao pode ser vazio")
	@Email(message = "e-mail invalido")
	String email
) {
}
