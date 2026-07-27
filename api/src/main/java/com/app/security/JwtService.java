package com.app.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

	private final SecretKey key;
	private final long expirationDays;

	public JwtService(
			@Value("${app.jwt.secret}") String secret,
			@Value("${app.jwt.expiration-days}") long expirationDays) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.expirationDays = expirationDays;
	}

	public String generateToken(UUID subject) {
		Instant now = Instant.now();
		return Jwts.builder()
			.subject(subject.toString())
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plus(expirationDays, ChronoUnit.DAYS)))
			.signWith(key)
			.compact();
	}

	public UUID parseSubject(String token) {
		Claims claims = Jwts.parser()
			.verifyWith(key)
			.build()
			.parseSignedClaims(token)
			.getPayload();
		return UUID.fromString(claims.getSubject());
	}
}
