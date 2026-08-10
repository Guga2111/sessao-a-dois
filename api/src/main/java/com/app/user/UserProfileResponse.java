package com.app.user;

import java.util.UUID;

/**
 * Contrato de resposta do perfil (epico 9, E9.15). Deliberadamente NAO reusa
 * {@code com.app.auth.UserSummary}: aquele record e parte do contrato de sessao
 * (login/{@code GET /api/auth/me}) e acopla-lo ao perfil faria mudar um arriscar o outro.
 * Os dois serem parecidos hoje e custo aceitavel.
 */
public record UserProfileResponse(UUID id, String name, String email) {

	static UserProfileResponse from(User user) {
		return new UserProfileResponse(user.getId(), user.getName(), user.getEmail());
	}
}
