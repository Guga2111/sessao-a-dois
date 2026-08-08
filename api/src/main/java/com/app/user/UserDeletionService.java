package com.app.user;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.auth.InvalidCredentialsException;
import com.app.auth.RefreshTokenService;
import com.app.common.ResourceNotFoundException;
import com.app.couple.CoupleFacade;
import com.app.match.MatchFacade;
import com.app.notification.NotificationFacade;
import com.app.security.RateLimitExceededException;
import com.app.security.RateLimitProperties;
import com.app.security.RateLimitProperties.Limit;
import com.app.security.RateLimitService;
import com.app.security.RateLimitService.RateLimitResult;
import com.app.security.SecurityAuditLogger;
import com.app.tracking.TrackingFacade;

/**
 * Exclusao da propria conta (epico 9, US-007) - o direito de eliminacao da LGPD sem destruir o
 * historico do ex-parceiro.
 *
 * <p>Este servico <b>orquestra</b> (E9.14): cada feature apaga o que e dela, atras da sua porta.
 * Injetar {@code MediaTrackRepository}/{@code MatchLikeRepository}/{@code NotificationRepository}
 * aqui seria o anti-pattern #5 e reintroduziria exatamente o acoplamento que a US-003 removeu.
 *
 * <p>O que <b>nao</b> e apagado, e por que: nenhuma linha de {@code couples} (a dissolucao e
 * {@code UPDATE dissolved_at} - {@code notification.couple_id} e {@code media_track.couple_id} tem
 * FK para ela) e nenhum {@code media_track} (pertence ao {@code couple_id}, e o historico que fica
 * com o ex-parceiro, D13).
 */
@Service
public class UserDeletionService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final CoupleFacade coupleFacade;
	private final TrackingFacade trackingFacade;
	private final RefreshTokenService refreshTokenService;
	private final MatchFacade matchFacade;
	private final NotificationFacade notificationFacade;
	private final RateLimitService rateLimitService;
	private final RateLimitProperties rateLimitProperties;
	private final SecurityAuditLogger securityAuditLogger;

	public UserDeletionService(UserRepository userRepository, PasswordEncoder passwordEncoder,
			CoupleFacade coupleFacade, TrackingFacade trackingFacade, RefreshTokenService refreshTokenService,
			MatchFacade matchFacade, NotificationFacade notificationFacade, RateLimitService rateLimitService,
			RateLimitProperties rateLimitProperties, SecurityAuditLogger securityAuditLogger) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.coupleFacade = coupleFacade;
		this.trackingFacade = trackingFacade;
		this.refreshTokenService = refreshTokenService;
		this.matchFacade = matchFacade;
		this.notificationFacade = notificationFacade;
		this.rateLimitService = rateLimitService;
		this.rateLimitProperties = rateLimitProperties;
		this.securityAuditLogger = securityAuditLogger;
	}

	/**
	 * Roda inteira em UMA transacao: ou a conta some com todos os dados pessoais, ou nada acontece.
	 * A ordem respeita as FKs de {@code V1}/{@code V5} - {@code user_review} e {@code refresh_token}
	 * referenciam {@code users} e precisam sair antes do {@code delete} final.
	 *
	 * <p>Senha errada lanca {@link InvalidCredentialsException} (401) <b>antes</b> de qualquer
	 * escrita.
	 */
	@Transactional
	public void deleteAccount(UUID userId, DeleteAccountRequest request) {
		enforceAccountDeleteRateLimit(userId);

		User user = userRepository.findById(userId)
			.orElseThrow(() -> new ResourceNotFoundException("usuario nao encontrado"));
		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new InvalidCredentialsException();
		}

		coupleFacade.dissolveIfActive(userId);
		trackingFacade.deleteUserData(userId);
		refreshTokenService.deleteAllForUser(userId);
		matchFacade.deleteUserData(userId);
		notificationFacade.deleteUserData(userId);
		userRepository.delete(user);

		securityAuditLogger.accountDeleted(userId);
	}

	/**
	 * Limite POR USUARIO ({@code app.rate-limit.account-delete}, 3/hora, E9.3). Nao passa pelo
	 * {@code RateLimitFilter}, que chaveia por IP e casa metodo+path.
	 */
	private void enforceAccountDeleteRateLimit(UUID userId) {
		Limit limit = rateLimitProperties.getAccountDelete();
		RateLimitResult result = rateLimitService.tryConsume("account-delete:user:" + userId, limit.getCapacity(),
				limit.getWindow());
		if (!result.allowed()) {
			throw new RateLimitExceededException(result.retryAfterSeconds());
		}
	}
}
