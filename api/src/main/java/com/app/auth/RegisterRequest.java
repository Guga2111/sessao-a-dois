package com.app.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

	@NotBlank(message = "nome nao pode ser vazio")
	String name,

	@NotBlank(message = "e-mail nao pode ser vazio")
	@Email(message = "e-mail invalido")
	String email,

	@NotBlank(message = "senha nao pode ser vazia")
	@Size(min = 8, message = "senha deve ter no minimo 8 caracteres")
	String password
) {
}
