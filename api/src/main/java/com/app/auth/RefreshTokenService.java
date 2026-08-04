package com.app.auth;

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

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();
	private static final int TOKEN_BYTES = 32;

	private final RefreshTokenRepository refreshTokenRepository;
	private final Duration ttl;

	public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
			@Value("${app.auth.refresh-token-ttl:30d}") Duration ttl) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.ttl = ttl;
	}

	/**
	 * Gera um valor opaco de 32 bytes (SecureRandom, Base64 URL-safe), persiste
	 * apenas o SHA-256 dele e devolve o valor em claro ao chamador - nenhum
	 * caminho de codigo recupera o valor original a partir do banco. TTL sempre
	 * conta a partir de agora (deslizante), inclusive nas emissoes por rotacao.
	 */
	@Transactional
	public String issue(UUID userId, String userAgent, String ip) {
		byte[] randomBytes = new byte[TOKEN_BYTES];
		SECURE_RANDOM.nextBytes(randomBytes);
		String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

		Instant now = Instant.now();
		RefreshToken token = new RefreshToken(userId, hash(rawToken), now.plus(ttl), userAgent, ip);
		refreshTokenRepository.save(token);

		return rawToken;
	}

	@Transactional(readOnly = true)
	public Optional<RefreshToken> findActive(String rawToken) {
		return refreshTokenRepository.findByTokenHash(hash(rawToken)).filter(RefreshToken::isActive);
	}

	@Transactional
	public void revokeFamily(UUID userId) {
		refreshTokenRepository.revokeAllActiveByUserId(userId, Instant.now());
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
