package com.app.user;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

	public UserProfileController(UserProfileService userProfileService) {
		this.userProfileService = userProfileService;
	}

	/** Altera apenas os campos presentes no corpo; a sessao atual continua valida. */
	@PatchMapping("/me")
	public ResponseEntity<UserProfileResponse> updateMe(@AuthenticationPrincipal UUID userId,
			@Valid @RequestBody UpdateProfileRequest request) {
		return ResponseEntity.ok(userProfileService.updateProfile(userId, request));
	}
}
