package com.app.auth;

import com.app.couple.Couple;
import com.app.couple.CoupleResponse;
import com.app.couple.CoupleService;
import com.app.couple.PartnerSummary;
import com.app.user.User;
import com.app.user.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
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
	private final AuthCookieService authCookieService;

	public AuthController(AuthService authService, CoupleService coupleService, UserRepository userRepository,
			AuthCookieService authCookieService) {
		this.authService = authService;
		this.coupleService = coupleService;
		this.userRepository = userRepository;
		this.authCookieService = authCookieService;
	}

	@PostMapping("/register")
	public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
		User user = authService.register(request);
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(new RegisterResponse(user.getId(), user.getName(), user.getEmail()));
	}

	@PostMapping("/login")
	public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
		String userAgent = servletRequest.getHeader("User-Agent");
		String ip = clientIp(servletRequest);
		AuthService.LoginResult result = authService.login(request, userAgent, ip);
		User user = result.user();
		UserSummary userSummary = new UserSummary(user.getId(), user.getName(), user.getEmail());
		CoupleResponse coupleResponse = coupleService.getCurrentCouple(user.getId())
			.map(couple -> toResponse(couple, user.getId()))
			.orElse(null);

		ResponseCookie accessCookie = authCookieService.accessTokenCookie(result.accessToken());
		ResponseCookie refreshCookie = authCookieService.refreshTokenCookie(result.refreshToken());

		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, accessCookie.toString())
			.header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
			.body(new LoginResponse(userSummary, coupleResponse));
	}

	/** Le o IP do cliente final de X-Forwarded-For (o nginx sempre envia esse header em producao). */
	private String clientIp(HttpServletRequest request) {
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (forwardedFor == null || forwardedFor.isBlank()) {
			return request.getRemoteAddr();
		}
		return forwardedFor.split(",")[0].trim();
	}

	private CoupleResponse toResponse(Couple couple, UUID currentUserId) {
		UUID partnerId = couple.getUser1Id().equals(currentUserId) ? couple.getUser2Id() : couple.getUser1Id();
		PartnerSummary partner = partnerId == null ? null : userRepository.findById(partnerId)
			.map(u -> new PartnerSummary(u.getId(), u.getName(), u.getEmail()))
			.orElse(null);
		return new CoupleResponse(couple.getId(), couple.getInviteCode(), partner, couple.getCreatedAt());
	}
}
