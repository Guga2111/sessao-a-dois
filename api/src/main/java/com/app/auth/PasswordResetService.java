package com.app.auth;

import com.app.user.User;

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

/**
 * Emissao do token de esqueci-minha-senha (US-005). Mesma receita criptografica
 * de RefreshTokenService: 32 bytes de SecureRandom em Base64 URL-safe sem
 * padding, so o SHA-256 em hex persistido - o valor em claro nunca vai para o
 * banco nem para log algum, so trafega no link devolvido ao chamador.
 */
@Service
public class PasswordResetService {

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();
	private static final int TOKEN_BYTES = 32;

	private final PasswordResetTokenRepository passwordResetTokenRepository;
	private final Duration ttl;
	private final String publicUrl;

	public PasswordResetService(PasswordResetTokenRepository passwordResetTokenRepository,
			@Value("${app.auth.password-reset-ttl:30m}") Duration ttl,
			@Value("${app.public-url:https://sessaoadois.luisgosampaio.com}") String publicUrl) {
		this.passwordResetTokenRepository = passwordResetTokenRepository;
		this.ttl = ttl;
		this.publicUrl = publicUrl;
	}

	/**
	 * Invalida todos os tokens anteriores ainda ativos do usuario, emite um
	 * token novo e devolve o link pronto para envio. O valor em claro so existe
	 * neste retorno - o banco guarda apenas o hash.
	 */
	@Transactional
	public String issueResetLink(User user) {
		Instant now = Instant.now();
		passwordResetTokenRepository.invalidateAllActiveByUserId(user.getId(), now);

		byte[] randomBytes = new byte[TOKEN_BYTES];
		SECURE_RANDOM.nextBytes(randomBytes);
		String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

		PasswordResetToken token = new PasswordResetToken(user.getId(), hash(rawToken), now.plus(ttl));
		passwordResetTokenRepository.save(token);

		return publicUrl + "/redefinir-senha?token=" + rawToken;
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
