package com.app.auth;

import com.app.couple.Couple;
import com.app.couple.CoupleResponse;
import com.app.couple.CoupleService;
import com.app.couple.PartnerSummary;
import com.app.security.ClientIpResolver;
import com.app.user.User;
import com.app.user.UserRepository;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;
	private final CoupleService coupleService;
	private final UserRepository userRepository;
	private final AuthCookieService authCookieService;
	private final ClientIpResolver clientIpResolver;

	public AuthController(AuthService authService, CoupleService coupleService, UserRepository userRepository,
			AuthCookieService authCookieService, ClientIpResolver clientIpResolver) {
		this.authService = authService;
		this.coupleService = coupleService;
		this.userRepository = userRepository;
		this.authCookieService = authCookieService;
		this.clientIpResolver = clientIpResolver;
	}

	@PostMapping("/register")
	public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
		User user = authService.register(request);
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(new RegisterResponse(user.getId(), user.getName(), user.getEmail()));
	}

	@PostMapping("/login")
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
			HttpServletRequest servletRequest) {
		String userAgent = servletRequest.getHeader("User-Agent");
		String ip = clientIpResolver.resolve(servletRequest);
		AuthService.LoginResult result = authService.login(request, userAgent, ip);
		User user = result.user();

		ResponseCookie accessCookie = authCookieService.accessTokenCookie(result.accessToken());
		ResponseCookie refreshCookie = authCookieService.refreshTokenCookie(result.refreshToken());

		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, accessCookie.toString())
			.header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
			.body(sessionResponse(user));
	}

	@PostMapping("/refresh")
	public ResponseEntity<Void> refresh(HttpServletRequest servletRequest) {
		Cookie cookie = WebUtils.getCookie(servletRequest, AuthCookieService.REFRESH_TOKEN_COOKIE);
		if (cookie == null || !StringUtils.hasText(cookie.getValue())) {
			throw new InvalidRefreshTokenException();
		}

		String userAgent = servletRequest.getHeader("User-Agent");
		String ip = clientIpResolver.resolve(servletRequest);
		AuthService.RefreshResult result = authService.refresh(cookie.getValue(), userAgent, ip);

		ResponseCookie accessCookie = authCookieService.accessTokenCookie(result.accessToken());
		ResponseCookie refreshCookie = authCookieService.refreshTokenCookie(result.refreshToken());

		return ResponseEntity.noContent()
			.header(HttpHeaders.SET_COOKIE, accessCookie.toString())
			.header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
			.build();
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(HttpServletRequest servletRequest) {
		Cookie cookie = WebUtils.getCookie(servletRequest, AuthCookieService.REFRESH_TOKEN_COOKIE);
		if (cookie != null && StringUtils.hasText(cookie.getValue())) {
			authService.logout(cookie.getValue());
		}

		ResponseCookie expiredAccessCookie = authCookieService.expiredAccessTokenCookie();
		ResponseCookie expiredRefreshCookie = authCookieService.expiredRefreshTokenCookie();

		return ResponseEntity.noContent()
			.header(HttpHeaders.SET_COOKIE, expiredAccessCookie.toString())
			.header(HttpHeaders.SET_COOKIE, expiredRefreshCookie.toString())
			.build();
	}

	@GetMapping("/me")
	public ResponseEntity<LoginResponse> me(@AuthenticationPrincipal UUID userId) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new IllegalStateException("usuario autenticado nao encontrado"));
		return ResponseEntity.ok(sessionResponse(user));
	}

	/** Monta o mesmo shape {user, couple} reutilizado por /login e /me. */
	private LoginResponse sessionResponse(User user) {
		UserSummary userSummary = new UserSummary(user.getId(), user.getName(), user.getEmail());
		CoupleResponse coupleResponse = coupleService.getCurrentCouple(user.getId())
			.map(couple -> toResponse(couple, user.getId()))
			.orElse(null);
		return new LoginResponse(userSummary, coupleResponse);
	}

	private CoupleResponse toResponse(Couple couple, UUID currentUserId) {
		UUID partnerId = couple.getUser1Id().equals(currentUserId) ? couple.getUser2Id() : couple.getUser1Id();
		PartnerSummary partner = partnerId == null ? null : userRepository.findById(partnerId)
			.map(u -> new PartnerSummary(u.getId(), u.getName(), u.getEmail()))
			.orElse(null);
		return new CoupleResponse(couple.getId(), couple.getInviteCode(), couple.getInviteCodeExpiresAt(), partner,
				couple.getCreatedAt());
	}
}
