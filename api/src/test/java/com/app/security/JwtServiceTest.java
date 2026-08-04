package com.app.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cobre a validacao fail-fast de {@code app.jwt.secret} no construtor de
 * {@link JwtService}, o TTL de 15 minutos do access token, a validacao de
 * iss/aud e o comportamento de assinatura/parse de token.
 */
class JwtServiceTest {

	private static final String VALID_SECRET = "a-valid-secret-with-at-least-32-bytes!!";
	private static final String OTHER_VALID_SECRET = "a-different-valid-secret-32-bytes-plus!!";
	private static final Duration TTL = Duration.ofMinutes(15);
	private static final String ISSUER = "sessao-a-dois";
	private static final String AUDIENCE = "sessao-a-dois-client";

	private static JwtService jwtService(String secret) {
		return new JwtService(secret, TTL, ISSUER, AUDIENCE);
	}

	private static SecretKey keyFor(String secret) {
		return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void throwsWhenSecretIsNull() {
		assertThatThrownBy(() -> new JwtService(null, TTL, ISSUER, AUDIENCE))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("JWT_SECRET");
	}

	@Test
	void throwsWhenSecretIsBlank() {
		assertThatThrownBy(() -> new JwtService("   ", TTL, ISSUER, AUDIENCE))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("JWT_SECRET");
	}

	@Test
	void throwsWhenSecretEqualsDevDefault() {
		assertThatThrownBy(() -> new JwtService("dev-only-secret-please-override-in-prod-32bytes+", TTL, ISSUER, AUDIENCE))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("JWT_SECRET");
	}

	@Test
	void throwsWhenSecretIsShorterThan32Bytes() {
		assertThatThrownBy(() -> new JwtService("too-short-secret", TTL, ISSUER, AUDIENCE))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("JWT_SECRET");
	}

	@Test
	void buildsNormallyWithValidSecret() {
		assertThat(VALID_SECRET.getBytes(StandardCharsets.UTF_8).length).isGreaterThanOrEqualTo(32);
		assertThat(jwtService(VALID_SECRET)).isNotNull();
	}

	@Test
	void roundTripsUuidThroughGenerateAndParse() {
		JwtService service = jwtService(VALID_SECRET);
		UUID subject = UUID.randomUUID();

		String token = service.generateToken(subject);

		assertThat(service.parseSubject(token)).isEqualTo(subject);
	}

	@Test
	void generateTokenAppliesFifteenMinuteTtl() {
		JwtService service = jwtService(VALID_SECRET);
		SecretKey key = keyFor(VALID_SECRET);

		String token = service.generateToken(UUID.randomUUID());
		Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();

		long ttlSeconds = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
		assertThat(ttlSeconds).isEqualTo(TTL.toSeconds());
	}

	@Test
	void parseSubjectThrowsWhenIssuerIsWrong() {
		JwtService service = jwtService(VALID_SECRET);
		SecretKey key = keyFor(VALID_SECRET);
		Instant now = Instant.now();
		String tokenWithWrongIssuer = Jwts.builder()
			.subject(UUID.randomUUID().toString())
			.issuer("outra-origem")
			.audience().single(AUDIENCE)
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plus(TTL)))
			.signWith(key)
			.compact();

		assertThatThrownBy(() -> service.parseSubject(tokenWithWrongIssuer))
			.isInstanceOf(IncorrectClaimException.class);
	}

	@Test
	void parseSubjectThrowsWhenAudienceIsWrong() {
		JwtService service = jwtService(VALID_SECRET);
		SecretKey key = keyFor(VALID_SECRET);
		Instant now = Instant.now();
		String tokenWithWrongAudience = Jwts.builder()
			.subject(UUID.randomUUID().toString())
			.issuer(ISSUER)
			.audience().single("outro-cliente")
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plus(TTL)))
			.signWith(key)
			.compact();

		assertThatThrownBy(() -> service.parseSubject(tokenWithWrongAudience))
			.isInstanceOf(IncorrectClaimException.class);
	}

	@Test
	void parseSubjectThrowsWhenTokenIsExpired() {
		JwtService service = jwtService(VALID_SECRET);
		SecretKey key = keyFor(VALID_SECRET);
		Instant now = Instant.now();
		String expiredToken = Jwts.builder()
			.subject(UUID.randomUUID().toString())
			.issuer(ISSUER)
			.audience().single(AUDIENCE)
			.issuedAt(Date.from(now.minus(Duration.ofMinutes(20))))
			.expiration(Date.from(now.minus(Duration.ofMinutes(5))))
			.signWith(key)
			.compact();

		assertThatThrownBy(() -> service.parseSubject(expiredToken))
			.isInstanceOf(ExpiredJwtException.class);
	}

	@Test
	void parseSubjectThrowsWhenSignedWithDifferentKey() {
		JwtService service = jwtService(VALID_SECRET);
		JwtService otherService = jwtService(OTHER_VALID_SECRET);
		String tokenSignedWithOtherKey = otherService.generateToken(UUID.randomUUID());

		assertThatThrownBy(() -> service.parseSubject(tokenSignedWithOtherKey))
			.isInstanceOf(SignatureException.class);
	}

	@Test
	void parseSubjectThrowsIllegalArgumentExceptionWhenSubjectIsNotAUuid() {
		JwtService service = jwtService(VALID_SECRET);
		SecretKey key = keyFor(VALID_SECRET);
		Instant now = Instant.now();
		String tokenWithNonUuidSubject = Jwts.builder()
			.subject("not-a-uuid")
			.issuer(ISSUER)
			.audience().single(AUDIENCE)
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plus(TTL)))
			.signWith(key)
			.compact();

		assertThatThrownBy(() -> service.parseSubject(tokenWithNonUuidSubject))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void parseSubjectThrowsWithoutNullPointerExceptionWhenTokenIsMalformed() {
		JwtService service = jwtService(VALID_SECRET);

		assertThatThrownBy(() -> service.parseSubject("not-a-jwt-token"))
			.isInstanceOf(MalformedJwtException.class);
	}
}
