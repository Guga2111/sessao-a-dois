package com.app.auth;

import com.app.security.JwtService;
import com.app.security.RateLimitExceededException;
import com.app.security.RateLimitProperties;
import com.app.security.RateLimitProperties.Limit;
import com.app.security.RateLimitService;
import com.app.security.RateLimitService.RateLimitResult;
import com.app.user.User;
import com.app.user.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

	/**
	 * Hash BCrypt de uma senha que nao pertence a ninguem, gerado uma unica vez
	 * (nunca por requisicao). Usado para nivelar o tempo de resposta do login
	 * quando o e-mail nao existe, para que o tempo de resposta nao denuncie
	 * quais e-mails tem conta (US-004).
	 */
	private static final String DUMMY_PASSWORD_HASH = "$2a$10$EySlaJxnwQMF1SFbaThV5udfNPTwgOO4kURuty9Lb3rbtiN21qVFK";

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final RefreshTokenService refreshTokenService;
	private final RateLimitService rateLimitService;
	private final RateLimitProperties rateLimitProperties;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
			RefreshTokenService refreshTokenService, RateLimitService rateLimitService,
			RateLimitProperties rateLimitProperties) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.refreshTokenService = refreshTokenService;
		this.rateLimitService = rateLimitService;
		this.rateLimitProperties = rateLimitProperties;
	}

	public User register(RegisterRequest request) {
		if (userRepository.existsByEmail(request.email())) {
			throw new EmailAlreadyExistsException();
		}

		User user = new User(request.name(), request.email(), passwordEncoder.encode(request.password()));
		return userRepository.save(user);
	}

	public LoginResult login(LoginRequest request, String userAgent, String ip) {
		enforceLoginRateLimit(request.email());

		Optional<User> maybeUser = userRepository.findByEmail(request.email());
		if (maybeUser.isEmpty()) {
			// Compara contra um hash dummy para gastar o mesmo tempo que o caminho de
			// senha incorreta gastaria, fechando a diferenca de timing entre "e-mail nao
			// existe" e "senha errada" (US-004). O resultado e sempre descartado.
			passwordEncoder.matches(request.password(), DUMMY_PASSWORD_HASH);
			throw new InvalidCredentialsException();
		}
		User user = maybeUser.get();

		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new InvalidCredentialsException();
		}

		String accessToken = jwtService.generateToken(user.getId());
		String refreshToken = refreshTokenService.issue(user.getId(), userAgent, ip);
		return new LoginResult(accessToken, refreshToken, user);
	}

	/**
	 * Limite por e-mail (US-003), alem do limite por IP ja aplicado pelo
	 * {@code RateLimitFilter} (US-002). Roda ANTES da busca do usuario e
	 * dispara mesmo quando o e-mail nao existe, para nao virar oraculo de
	 * enumeracao. A chave e normalizada (minusculas, sem espacos nas pontas)
	 * para que variacoes de caixa nao multipliquem o teto.
	 */
	private void enforceLoginRateLimit(String email) {
		String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
		Limit limit = rateLimitProperties.getLoginByEmail();
		RateLimitResult result = rateLimitService.tryConsume("login:email:" + normalizedEmail, limit.getCapacity(),
				limit.getWindow());
		if (!result.allowed()) {
			throw new RateLimitExceededException(result.retryAfterSeconds());
		}
	}

	/** Valida e rotaciona o refresh token apresentado, emitindo um novo access token para o mesmo usuario. */
	public RefreshResult refresh(String rawRefreshToken, String userAgent, String ip) {
		RefreshTokenService.RotationResult rotation = refreshTokenService.rotate(rawRefreshToken, userAgent, ip);
		String accessToken = jwtService.generateToken(rotation.userId());
		return new RefreshResult(accessToken, rotation.refreshToken());
	}

	/** Revoga no servidor o refresh token apresentado no logout - idempotente, ver RefreshTokenService.revoke. */
	public void logout(String rawRefreshToken) {
		refreshTokenService.revoke(rawRefreshToken);
	}

	public record LoginResult(String accessToken, String refreshToken, User user) {
	}

	public record RefreshResult(String accessToken, String refreshToken) {
	}
}
