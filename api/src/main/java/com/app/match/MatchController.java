package com.app.match;

import com.app.couple.CoupleFacade;

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
	private final CoupleFacade coupleFacade;

	public MatchController(MatchService matchService, CoupleFacade coupleFacade) {
		this.matchService = matchService;
		this.coupleFacade = coupleFacade;
	}

	@PostMapping("/like")
	public ResponseEntity<LikeResponse> like(@AuthenticationPrincipal UUID userId,
			@Valid @RequestBody LikeRequest request) {
		UUID coupleId = coupleFacade.requireActiveCoupleId(userId);

		return ResponseEntity.ok(matchService.like(coupleId, userId, request));
	}

	@PostMapping("/reject")
	public ResponseEntity<Void> reject(@AuthenticationPrincipal UUID userId,
			@Valid @RequestBody LikeRequest request) {
		UUID coupleId = coupleFacade.requireActiveCoupleId(userId);

		matchService.reject(coupleId, userId, request);
		return ResponseEntity.ok().build();
	}

	@GetMapping("/pending")
	public ResponseEntity<List<PendingMatchDto>> pending(@AuthenticationPrincipal UUID userId) {
		UUID coupleId = coupleFacade.requireActiveCoupleId(userId);

		return ResponseEntity.ok(matchService.getPending(coupleId, userId));
	}
}
