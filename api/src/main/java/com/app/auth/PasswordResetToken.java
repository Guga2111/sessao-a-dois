package com.app.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_token", indexes = {
		@Index(name = "idx_password_reset_token_user", columnList = "user_id"),
		@Index(name = "idx_password_reset_token_expires", columnList = "expires_at") })
public class PasswordResetToken {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	/** Nunca guarda o valor em claro do token - apenas o SHA-256 em hex. */
	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected PasswordResetToken() {
	}

	public PasswordResetToken(UUID userId, String tokenHash, Instant expiresAt) {
		this.userId = userId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getUsedAt() {
		return usedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public boolean isExpired(Instant now) {
		return expiresAt.isBefore(now);
	}

	public boolean isUsed() {
		return usedAt != null;
	}

	public void markUsed(Instant now) {
		this.usedAt = now;
	}
}
