package com.app.couple;

import com.app.user.User;
import com.app.user.UserRepository;

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
	private final UserRepository userRepository;

	public CoupleController(CoupleService coupleService, UserRepository userRepository) {
		this.coupleService = coupleService;
		this.userRepository = userRepository;
	}

	@PostMapping
	public ResponseEntity<CoupleResponse> create(@AuthenticationPrincipal UUID userId) {
		Couple couple = coupleService.createCouple(userId);
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(couple, userId));
	}

	@GetMapping("/me")
	public ResponseEntity<CoupleResponse> me(@AuthenticationPrincipal UUID userId) {
		return coupleService.getCurrentCouple(userId)
			.map(couple -> ResponseEntity.ok(toResponse(couple, userId)))
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PostMapping("/join")
	public ResponseEntity<CoupleResponse> join(@AuthenticationPrincipal UUID userId,
			@Valid @RequestBody JoinCoupleRequest request) {
		Couple couple = coupleService.joinCouple(userId, request.inviteCode());
		return ResponseEntity.ok(toResponse(couple, userId));
	}

	private CoupleResponse toResponse(Couple couple, UUID currentUserId) {
		UUID partnerId = couple.getUser1Id().equals(currentUserId) ? couple.getUser2Id() : couple.getUser1Id();
		PartnerSummary partner = partnerId == null ? null : userRepository.findById(partnerId)
			.map(this::toPartnerSummary)
			.orElse(null);
		return new CoupleResponse(couple.getId(), couple.getInviteCode(), couple.getInviteCodeExpiresAt(), partner,
				couple.getCreatedAt());
	}

	private PartnerSummary toPartnerSummary(User user) {
		return new PartnerSummary(user.getId(), user.getName(), user.getEmail());
	}
}
