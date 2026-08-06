package com.app.auth;

import com.app.security.SecurityAuditLogger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

	private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();
	private static final int TOKEN_BYTES = 32;
	/** Limite defensivo de saltos por replaced_by_id dentro da janela de graca - o encadeamento e sempre curto na pratica, isso so evita loop em dado corrompido. */
	private static final int MAX_REUSE_CHAIN_HOPS = 10;

	private final RefreshTokenRepository refreshTokenRepository;
	private final Duration ttl;
	private final Duration reuseGrace;
	private final SecurityAuditLogger securityAuditLogger;

	public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
			@Value("${app.auth.refresh-token-ttl:30d}") Duration ttl,
			@Value("${app.auth.refresh-reuse-grace:30s}") Duration reuseGrace,
			SecurityAuditLogger securityAuditLogger) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.ttl = ttl;
		this.reuseGrace = reuseGrace;
		this.securityAuditLogger = securityAuditLogger;
	}

	/**
	 * Gera um valor opaco de 32 bytes (SecureRandom, Base64 URL-safe), persiste
	 * apenas o SHA-256 dele e devolve o valor em claro ao chamador - nenhum
	 * caminho de codigo recupera o valor original a partir do banco. TTL sempre
	 * conta a partir de agora (deslizante), inclusive nas emissoes por rotacao.
	 */
	@Transactional
	public String issue(UUID userId, String userAgent, String ip) {
		return issueToken(userId, userAgent, ip).rawToken();
	}

	@Transactional(readOnly = true)
	public Optional<RefreshToken> findActive(String rawToken) {
		return refreshTokenRepository.findByTokenHash(hash(rawToken)).filter(RefreshToken::isActive);
	}

	@Transactional
	public void revokeFamily(UUID userId) {
		refreshTokenRepository.revokeAllActiveByUserId(userId, Instant.now());
	}

	/**
	 * Revoga o refresh token apresentado no logout, se ele existir e ainda nao
	 * estiver revogado. Idempotente: token desconhecido ou ja revogado nao
	 * lanca excecao - o efeito desejado (o token nao vale mais) ja e verdade.
	 */
	@Transactional
	public void revoke(String rawToken) {
		refreshTokenRepository.findByTokenHash(hash(rawToken))
			.filter(token -> token.getRevokedAt() == null)
			.ifPresent(token -> {
				token.setRevokedAt(Instant.now());
				securityAuditLogger.logout(token.getUserId());
			});
	}

	/**
	 * Valida o refresh token apresentado, revoga-o e emite um novo par (access
	 * fica a cargo do chamador - aqui so o refresh), gravando replaced_by_id no
	 * registro revogado. TTL do novo token e sempre now + ttl (deslizante,
	 * decisao E5), nunca herda o vencimento do que substituiu.
	 *
	 * <p>DETECCAO DE REUSO (decisao E4): um token JA REVOGADO revoga a familia
	 * inteira, EXCETO se a revogacao aconteceu ha menos de {@code reuseGrace} -
	 * nesse caso, em vez de punir (duas abas podem ter disparado refresh quase
	 * juntas), o servidor segue replaced_by_id e rotaciona a partir do token que
	 * substituiu o apresentado, sem tocar na familia. Um token EXPIRADO (nunca
	 * revogado) e rejeitado sem revogar a familia - expiracao natural nao e
	 * sinal de roubo.
	 */
	@Transactional
	public RotationResult rotate(String rawToken, String userAgent, String ip) {
		RefreshToken current = refreshTokenRepository.findByTokenHash(hash(rawToken))
			.orElseThrow(InvalidRefreshTokenException::new);
		return rotate(current, userAgent, ip, 0);
	}

	private RotationResult rotate(RefreshToken current, String userAgent, String ip, int hop) {
		if (current.getRevokedAt() != null) {
			return handleRevokedToken(current, userAgent, ip, hop);
		}
		if (!current.isActive()) {
			throw new InvalidRefreshTokenException();
		}
		return performRotation(current, userAgent, ip);
	}

	private RotationResult handleRevokedToken(RefreshToken current, String userAgent, String ip, int hop) {
		if (hop < MAX_REUSE_CHAIN_HOPS && isWithinGrace(current.getRevokedAt()) && current.getReplacedById() != null) {
			Optional<RefreshToken> substitute = refreshTokenRepository.findById(current.getReplacedById());
			if (substitute.isPresent()) {
				return rotate(substitute.get(), userAgent, ip, hop + 1);
			}
		}
		log.warn("Reuso de refresh token detectado para usuario {} a partir do IP {}", current.getUserId(), ip);
		securityAuditLogger.refreshReuseDetected(current.getUserId(), ip);
		revokeFamily(current.getUserId());
		throw new RefreshReuseDetectedException();
	}

	private RotationResult performRotation(RefreshToken current, String userAgent, String ip) {
		IssuedToken issued = issueToken(current.getUserId(), userAgent, ip);
		current.setRevokedAt(Instant.now());
		current.setReplacedById(issued.entity().getId());
		securityAuditLogger.refresh(current.getUserId());
		return new RotationResult(current.getUserId(), issued.rawToken());
	}

	private boolean isWithinGrace(Instant revokedAt) {
		return revokedAt != null && revokedAt.plus(reuseGrace).isAfter(Instant.now());
	}

	private IssuedToken issueToken(UUID userId, String userAgent, String ip) {
		byte[] randomBytes = new byte[TOKEN_BYTES];
		SECURE_RANDOM.nextBytes(randomBytes);
		String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

		Instant now = Instant.now();
		RefreshToken token = new RefreshToken(userId, hash(rawToken), now.plus(ttl), userAgent, ip);
		refreshTokenRepository.save(token);

		return new IssuedToken(rawToken, token);
	}

	public record RotationResult(UUID userId, String refreshToken) {
	}

	private record IssuedToken(String rawToken, RefreshToken entity) {
	}

	private static String hash(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hashBytes);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 nao disponivel", ex);
		}
	}
}
