package com.app.auth;

import com.app.security.JwtService;
import com.app.security.RateLimitExceededException;
import com.app.security.RateLimitProperties;
import com.app.security.RateLimitProperties.Limit;
import com.app.security.RateLimitService;
import com.app.security.RateLimitService.RateLimitResult;
import com.app.security.SecurityAuditLogger;
import com.app.user.User;
import com.app.user.UserRepository;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

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
	private final SecurityAuditLogger securityAuditLogger;
	private final PasswordResetDispatcher passwordResetDispatcher;
	private final PasswordResetService passwordResetService;
	private final ApplicationEventPublisher eventPublisher;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
			RefreshTokenService refreshTokenService, RateLimitService rateLimitService,
			RateLimitProperties rateLimitProperties, SecurityAuditLogger securityAuditLogger,
			PasswordResetDispatcher passwordResetDispatcher, PasswordResetService passwordResetService,
			ApplicationEventPublisher eventPublisher) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.refreshTokenService = refreshTokenService;
		this.rateLimitService = rateLimitService;
		this.rateLimitProperties = rateLimitProperties;
		this.securityAuditLogger = securityAuditLogger;
		this.passwordResetDispatcher = passwordResetDispatcher;
		this.passwordResetService = passwordResetService;
		this.eventPublisher = eventPublisher;
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
			securityAuditLogger.loginFailure(request.email(), ip);
			throw new InvalidCredentialsException();
		}
		User user = maybeUser.get();

		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			securityAuditLogger.loginFailure(request.email(), ip);
			throw new InvalidCredentialsException();
		}

		String accessToken = jwtService.generateToken(user.getId());
		String refreshToken = refreshTokenService.issue(user.getId(), userAgent, ip);
		securityAuditLogger.loginSuccess(user.getId(), request.email(), ip);
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

	/** Usado por GET /api/auth/me para carregar o usuario ja autenticado pelo filtro JWT. */
	public User findAuthenticatedUser(UUID userId) {
		return userRepository.findById(userId)
			.orElseThrow(() -> new IllegalStateException("usuario autenticado nao encontrado"));
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

	/**
	 * Troca a senha de quem ja esta autenticado (epico 9, US-006) e derruba TODAS as sessoes,
	 * inclusive a que fez a troca (E9.8): se a senha estava comprometida, a sessao do atacante
	 * morre junto. Senha atual errada nao altera nada - nem a senha, nem as sessoes.
	 */
	@Transactional
	public void changePassword(UUID userId, ChangePasswordRequest request) {
		enforcePasswordChangeRateLimit(userId);

		User user = findAuthenticatedUser(userId);
		if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
			throw new InvalidCredentialsException();
		}

		user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
		userRepository.save(user);
		refreshTokenService.revokeFamily(userId);
		securityAuditLogger.passwordChanged(userId);
		eventPublisher.publishEvent(new PasswordChangedNoticeEvent(userId, user.getEmail(), user.getName()));
	}

	/**
	 * Limite POR USUARIO ({@code app.rate-limit.password-change}, 5/hora). Nao passa pelo
	 * {@code RateLimitFilter}, que chaveia por IP e casa metodo+path.
	 */
	private void enforcePasswordChangeRateLimit(UUID userId) {
		Limit limit = rateLimitProperties.getPasswordChange();
		RateLimitResult result = rateLimitService.tryConsume("password-change:user:" + userId, limit.getCapacity(),
				limit.getWindow());
		if (!result.allowed()) {
			throw new RateLimitExceededException(result.retryAfterSeconds());
		}
	}

	/**
	 * POST /api/auth/forgot-password (US-006). Responde sempre 202 no controller,
	 * independentemente do e-mail existir - aqui so decidimos SE ha trabalho a fazer.
	 * A emissao do token e o envio do e-mail rodam fora deste metodo (PasswordResetDispatcher,
	 * @Async), entao um provedor fora do ar nunca atrasa nem altera a resposta.
	 */
	public void forgotPassword(String email) {
		enforceForgotPasswordRateLimit(email);
		securityAuditLogger.passwordResetRequested(email);

		userRepository.findByEmail(email).ifPresent(passwordResetDispatcher::dispatch);
	}

	/**
	 * Limite por e-mail alvo (US-008), alem do limite por IP ja aplicado pelo
	 * {@code RateLimitFilter}. Roda ANTES da busca do usuario e dispara mesmo
	 * quando o e-mail nao existe, para nao virar oraculo de enumeracao - mesmo
	 * padrao de {@link #enforceLoginRateLimit(String)}.
	 */
	private void enforceForgotPasswordRateLimit(String email) {
		String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
		Limit limit = rateLimitProperties.getForgotPasswordByEmail();
		RateLimitResult result = rateLimitService.tryConsume("forgot-password:email:" + normalizedEmail,
				limit.getCapacity(), limit.getWindow());
		if (!result.allowed()) {
			throw new RateLimitExceededException(result.retryAfterSeconds());
		}
	}

	/**
	 * POST /api/auth/reset-password (US-007). Consome o token (uso unico, mesmo erro
	 * generico para inexistente/expirado/ja usado), grava a senha nova com o mesmo
	 * PasswordEncoder do login e derruba TODAS as sessoes do usuario (E9.8) - a
	 * resposta nao emite nenhum cookie, o reset nao autentica quem o fez.
	 */
	@Transactional
	public void resetPassword(String rawToken, String newPassword) {
		UUID userId = passwordResetService.consumeToken(rawToken);
		User user = findAuthenticatedUser(userId);

		user.setPasswordHash(passwordEncoder.encode(newPassword));
		userRepository.save(user);
		refreshTokenService.revokeFamily(userId);
		securityAuditLogger.passwordResetCompleted(userId);
		eventPublisher.publishEvent(new PasswordChangedNoticeEvent(userId, user.getEmail(), user.getName()));
	}

	public record LoginResult(String accessToken, String refreshToken, User user) {
	}

	public record RefreshResult(String accessToken, String refreshToken) {
	}
}
