package com.app.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code PATCH /api/user/me} (epico 9, US-005). Semantica de PATCH: um campo
 * <b>ausente</b> (null) nao e alterado, entao nenhum campo pode ser {@code @NotBlank} - a
 * anotacao reprovaria justamente o caso normal de "so mandei o outro campo".
 *
 * <p>O que substitui o {@code @NotBlank} e o par de anotacoes que ignora null por
 * especificacao: {@code @Pattern} exige pelo menos um caractere nao-branco quando o campo
 * VEM, e {@code @Email}/{@code @Size(min = 1)} cobrem o e-mail (o validador de
 * {@code @Email} considera a string vazia valida, dai o {@code @Size}).
 */
public record UpdateProfileRequest(

	@Pattern(regexp = ".*\\S.*", message = "nome nao pode ser vazio")
	@Size(max = 255, message = "nome deve ter no maximo 255 caracteres")
	String name,

	@Size(min = 1, max = 255, message = "e-mail deve ter entre 1 e 255 caracteres")
	@Email(message = "e-mail invalido")
	String email
) {
}
