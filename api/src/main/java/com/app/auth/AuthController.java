package com.app.auth;

import com.app.couple.Couple;
import com.app.couple.CoupleResponse;
import com.app.couple.CoupleService;
import com.app.couple.PartnerSummary;
import com.app.user.User;
import com.app.user.UserRepository;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;
	private final CoupleService coupleService;
	private final UserRepository userRepository;

	public AuthController(AuthService authService, CoupleService coupleService, UserRepository userRepository) {
		this.authService = authService;
		this.coupleService = coupleService;
		this.userRepository = userRepository;
	}

	@PostMapping("/register")
	public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
		User user = authService.register(request);
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(new RegisterResponse(user.getId(), user.getName(), user.getEmail()));
	}

	@PostMapping("/login")
	public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
		AuthService.LoginResult result = authService.login(request);
		User user = result.user();
		UserSummary userSummary = new UserSummary(user.getId(), user.getName(), user.getEmail());
		CoupleResponse coupleResponse = coupleService.getCurrentCouple(user.getId())
			.map(couple -> toResponse(couple, user.getId()))
			.orElse(null);
		return ResponseEntity.ok(new LoginResponse(result.token(), userSummary, coupleResponse));
	}

	private CoupleResponse toResponse(Couple couple, UUID currentUserId) {
		UUID partnerId = couple.getUser1Id().equals(currentUserId) ? couple.getUser2Id() : couple.getUser1Id();
		PartnerSummary partner = partnerId == null ? null : userRepository.findById(partnerId)
			.map(u -> new PartnerSummary(u.getId(), u.getName(), u.getEmail()))
			.orElse(null);
		return new CoupleResponse(couple.getId(), couple.getInviteCode(), partner, couple.getCreatedAt());
	}
}
