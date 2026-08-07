package com.app.user;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Borda HTTP do perfil (epico 9, US-005) - a primeira de {@code com.app.user}. Depende
 * apenas do servico: injetar {@code UserRepository} aqui seria o anti-pattern #1 da
 * {@code docs/ARCHITECTURE.md}.
 */
@RestController
@RequestMapping("/api/user")
public class UserProfileController {

	private final UserProfileService userProfileService;
	private final UserDeletionService userDeletionService;

	public UserProfileController(UserProfileService userProfileService, UserDeletionService userDeletionService) {
		this.userProfileService = userProfileService;
		this.userDeletionService = userDeletionService;
	}

	/** Altera apenas os campos presentes no corpo; a sessao atual continua valida. */
	@PatchMapping("/me")
	public ResponseEntity<UserProfileResponse> updateMe(@AuthenticationPrincipal UUID userId,
			@Valid @RequestBody UpdateProfileRequest request) {
		return ResponseEntity.ok(userProfileService.updateProfile(userId, request));
	}

	/**
	 * Exclusao da propria conta (epico 9, US-007). Exige a senha no corpo (E9.12) e responde 204.
	 * Nao limpa os cookies de sessao aqui de proposito: os refresh tokens ja foram apagados e o
	 * access token deixa de resolver um usuario existente na requisicao seguinte - quem limpa o
	 * estado local e a US-012, que ja leva o usuario para a landing.
	 */
	@DeleteMapping("/me")
	public ResponseEntity<Void> deleteMe(@AuthenticationPrincipal UUID userId,
			@Valid @RequestBody DeleteAccountRequest request) {
		userDeletionService.deleteAccount(userId, request);
		return ResponseEntity.noContent().build();
	}
}
