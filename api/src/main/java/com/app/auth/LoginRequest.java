package com.app.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

	@NotBlank(message = "e-mail nao pode ser vazio")
	@Email(message = "e-mail invalido")
	String email,

	@NotBlank(message = "senha nao pode ser vazia")
	String password
) {
}
