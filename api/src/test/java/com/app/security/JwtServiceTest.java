package com.app.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cobre a validacao fail-fast de {@code app.jwt.secret} no construtor de
 * {@link JwtService} e o comportamento de assinatura/parse de token.
 */
class JwtServiceTest {

	private static final String VALID_SECRET = "a-valid-secret-with-at-least-32-bytes!!";
	private static final String OTHER_VALID_SECRET = "a-different-valid-secret-32-bytes-plus!!";

	@Test
	void throwsWhenSecretIsNull() {
		assertThatThrownBy(() -> new JwtService(null, 7))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("JWT_SECRET");
	}

	@Test
	void throwsWhenSecretIsBlank() {
		assertThatThrownBy(() -> new JwtService("   ", 7))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("JWT_SECRET");
	}

	@Test
	void throwsWhenSecretEqualsDevDefault() {
		assertThatThrownBy(() -> new JwtService("dev-only-secret-please-override-in-prod-32bytes+", 7))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("JWT_SECRET");
	}

	@Test
	void throwsWhenSecretIsShorterThan32Bytes() {
		assertThatThrownBy(() -> new JwtService("too-short-secret", 7))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("JWT_SECRET");
	}

	@Test
	void buildsNormallyWithValidSecret() {
		assertThat(VALID_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isGreaterThanOrEqualTo(32);
		assertThat(new JwtService(VALID_SECRET, 7)).isNotNull();
	}

	@Test
	void roundTripsUuidThroughGenerateAndParse() {
		JwtService jwtService = new JwtService(VALID_SECRET, 7);
		UUID subject = UUID.randomUUID();

		String token = jwtService.generateToken(subject);

		assertThat(jwtService.parseSubject(token)).isEqualTo(subject);
	}

	@Test
	void parseSubjectThrowsWhenTokenIsExpired() {
		JwtService jwtService = new JwtService(VALID_SECRET, 7);
		SecretKey key = Keys.hmacShaKeyFor(VALID_SECRET.getBytes(StandardCharsets.UTF_8));
		Instant now = Instant.now();
		String expiredToken = Jwts.builder()
			.subject(UUID.randomUUID().toString())
			.issuedAt(Date.from(now.minus(2, ChronoUnit.DAYS)))
			.expiration(Date.from(now.minus(1, ChronoUnit.DAYS)))
			.signWith(key)
			.compact();

		assertThatThrownBy(() -> jwtService.parseSubject(expiredToken))
			.isInstanceOf(ExpiredJwtException.class);
	}

	@Test
	void parseSubjectThrowsWhenSignedWithDifferentKey() {
		JwtService jwtService = new JwtService(VALID_SECRET, 7);
		JwtService otherJwtService = new JwtService(OTHER_VALID_SECRET, 7);
		String tokenSignedWithOtherKey = otherJwtService.generateToken(UUID.randomUUID());

		assertThatThrownBy(() -> jwtService.parseSubject(tokenSignedWithOtherKey))
			.isInstanceOf(SignatureException.class);
	}

	@Test
	void parseSubjectThrowsIllegalArgumentExceptionWhenSubjectIsNotAUuid() {
		JwtService jwtService = new JwtService(VALID_SECRET, 7);
		SecretKey key = Keys.hmacShaKeyFor(VALID_SECRET.getBytes(StandardCharsets.UTF_8));
		Instant now = Instant.now();
		String tokenWithNonUuidSubject = Jwts.builder()
			.subject("not-a-uuid")
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plus(7, ChronoUnit.DAYS)))
			.signWith(key)
			.compact();

		assertThatThrownBy(() -> jwtService.parseSubject(tokenWithNonUuidSubject))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void parseSubjectThrowsWithoutNullPointerExceptionWhenTokenIsMalformed() {
		JwtService jwtService = new JwtService(VALID_SECRET, 7);

		assertThatThrownBy(() -> jwtService.parseSubject("not-a-jwt-token"))
			.isInstanceOf(MalformedJwtException.class);
	}
}
