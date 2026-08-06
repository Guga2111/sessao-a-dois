package com.app.couple;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/couple")
public class CoupleController {

	private final CoupleService coupleService;
	private final CoupleResponseMapper coupleResponseMapper;

	public CoupleController(CoupleService coupleService, CoupleResponseMapper coupleResponseMapper) {
		this.coupleService = coupleService;
		this.coupleResponseMapper = coupleResponseMapper;
	}

	@PostMapping
	public ResponseEntity<CoupleResponse> create(@AuthenticationPrincipal UUID userId) {
		Couple couple = coupleService.createCouple(userId);
		return ResponseEntity.status(HttpStatus.CREATED).body(coupleResponseMapper.toResponse(couple, userId));
	}

	@GetMapping("/me")
	public ResponseEntity<CoupleResponse> me(@AuthenticationPrincipal UUID userId) {
		return coupleService.getCurrentCouple(userId)
			.map(couple -> ResponseEntity.ok(coupleResponseMapper.toResponse(couple, userId)))
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PostMapping("/join")
	public ResponseEntity<CoupleResponse> join(@AuthenticationPrincipal UUID userId,
			@Valid @RequestBody JoinCoupleRequest request) {
		Couple couple = coupleService.joinCouple(userId, request.inviteCode());
		return ResponseEntity.ok(coupleResponseMapper.toResponse(couple, userId));
	}

	@PostMapping("/invite-code/regenerate")
	public ResponseEntity<CoupleResponse> regenerateInviteCode(@AuthenticationPrincipal UUID userId) {
		Couple couple = coupleService.regenerateInviteCode(userId);
		return ResponseEntity.ok(coupleResponseMapper.toResponse(couple, userId));
	}
}
