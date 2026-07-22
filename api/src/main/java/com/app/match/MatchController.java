package com.app.match;

import com.app.couple.Couple;
import com.app.couple.CoupleService;
import com.app.tracking.ResourceNotFoundException;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
