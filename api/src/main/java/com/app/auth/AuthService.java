package com.app.auth;

import com.app.security.JwtService;
import com.app.user.User;
import com.app.user.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final RefreshTokenService refreshTokenService;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
			RefreshTokenService refreshTokenService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.refreshTokenService = refreshTokenService;
	}

	public User register(RegisterRequest request) {
		if (userRepository.existsByEmail(request.email())) {
			throw new EmailAlreadyExistsException();
		}

		User user = new User(request.name(), request.email(), passwordEncoder.encode(request.password()));
		return userRepository.save(user);
	}

	public LoginResult login(LoginRequest request, String userAgent, String ip) {
		User user = userRepository.findByEmail(request.email())
			.orElseThrow(InvalidCredentialsException::new);

		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new InvalidCredentialsException();
		}

		String accessToken = jwtService.generateToken(user.getId());
		String refreshToken = refreshTokenService.issue(user.getId(), userAgent, ip);
		return new LoginResult(accessToken, refreshToken, user);
	}

	/** Valida e rotaciona o refresh token apresentado, emitindo um novo access token para o mesmo usuario. */
	public RefreshResult refresh(String rawRefreshToken, String userAgent, String ip) {
		RefreshTokenService.RotationResult rotation = refreshTokenService.rotate(rawRefreshToken, userAgent, ip);
		String accessToken = jwtService.generateToken(rotation.userId());
		return new RefreshResult(accessToken, rotation.refreshToken());
	}

	public record LoginResult(String accessToken, String refreshToken, User user) {
	}

	public record RefreshResult(String accessToken, String refreshToken) {
	}
}
