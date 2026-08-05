package com.app.match;

import com.app.couple.Couple;
import com.app.couple.CoupleService;
import com.app.common.ResourceNotFoundException;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/match")
public class MatchController {

	private final MatchService matchService;
	private final CoupleService coupleService;

	public MatchController(MatchService matchService, CoupleService coupleService) {
		this.matchService = matchService;
		this.coupleService = coupleService;
	}

	@PostMapping("/like")
	public ResponseEntity<LikeResponse> like(@AuthenticationPrincipal UUID userId,
			@Valid @RequestBody LikeRequest request) {
		Couple couple = coupleService.getCurrentCouple(userId)
			.orElseThrow(() -> new ResourceNotFoundException("usuario nao pertence a nenhum casal"));

		return ResponseEntity.ok(matchService.like(couple.getId(), userId, request));
	}

	@PostMapping("/reject")
	public ResponseEntity<Void> reject(@AuthenticationPrincipal UUID userId,
			@Valid @RequestBody LikeRequest request) {
		Couple couple = coupleService.getCurrentCouple(userId)
			.orElseThrow(() -> new ResourceNotFoundException("usuario nao pertence a nenhum casal"));

		matchService.reject(couple.getId(), userId, request);
		return ResponseEntity.ok().build();
	}

	@GetMapping("/pending")
	public ResponseEntity<List<PendingMatchDto>> pending(@AuthenticationPrincipal UUID userId) {
		Couple couple = coupleService.getCurrentCouple(userId)
			.orElseThrow(() -> new ResourceNotFoundException("usuario nao pertence a nenhum casal"));

		return ResponseEntity.ok(matchService.getPending(couple.getId(), userId));
	}
}
