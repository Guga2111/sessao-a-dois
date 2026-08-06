package com.app.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

	private static final String DEV_DEFAULT_SECRET = "dev-only-secret-please-override-in-prod-32bytes+";
	private static final int MIN_SECRET_BYTES = 32;

	private final SecretKey key;
	private final Duration accessTokenTtl;
	private final String issuer;
	private final String audience;

	public JwtService(
			@Value("${app.jwt.secret}") String secret,
			@Value("${app.jwt.access-token-ttl:15m}") Duration accessTokenTtl,
			@Value("${app.jwt.issuer:sessao-a-dois}") String issuer,
			@Value("${app.jwt.audience:sessao-a-dois-client}") String audience) {
		validateSecret(secret);
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.accessTokenTtl = accessTokenTtl;
		this.issuer = issuer;
		this.audience = audience;
	}

	private static void validateSecret(String secret) {
		if (!StringUtils.hasText(secret)) {
			throw new IllegalStateException(
				"JWT_SECRET nao configurado. Defina a variavel de ambiente JWT_SECRET antes de iniciar a aplicacao.");
		}
		if (secret.equals(DEV_DEFAULT_SECRET)) {
			throw new IllegalStateException(
				"JWT_SECRET esta igual ao valor padrao de desenvolvimento. Defina um segredo proprio na variavel de ambiente JWT_SECRET antes de iniciar a aplicacao.");
		}
		int secretBytes = secret.getBytes(StandardCharsets.UTF_8).length;
		if (secretBytes < MIN_SECRET_BYTES) {
			throw new IllegalStateException(
				"JWT_SECRET tem apenas " + secretBytes + " bytes; sao necessarios pelo menos " + MIN_SECRET_BYTES
					+ " bytes. Defina um segredo maior na variavel de ambiente JWT_SECRET.");
		}
	}

	public String generateToken(UUID subject) {
		Instant now = Instant.now();
		return Jwts.builder()
			.subject(subject.toString())
			.issuer(issuer)
			.audience().single(audience)
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plus(accessTokenTtl)))
			.signWith(key)
			.compact();
	}

	public UUID parseSubject(String token) {
		Claims claims = Jwts.parser()
			.verifyWith(key)
			.requireIssuer(issuer)
			.requireAudience(audience)
			.build()
			.parseSignedClaims(token)
			.getPayload();
		return UUID.fromString(claims.getSubject());
	}
}
