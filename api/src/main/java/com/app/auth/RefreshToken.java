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
@Table(name = "refresh_token", indexes = {
		@Index(name = "idx_refresh_token_user", columnList = "user_id"),
		@Index(name = "idx_refresh_token_expires", columnList = "expires_at") })
public class RefreshToken {

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

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "replaced_by_id")
	private UUID replacedById;

	@Column(name = "user_agent", length = 255)
	private String userAgent;

	@Column(name = "ip", length = 45)
	private String ip;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected RefreshToken() {
	}

	public RefreshToken(UUID userId, String tokenHash, Instant expiresAt, String userAgent, String ip) {
		this.userId = userId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.userAgent = userAgent;
		this.ip = ip;
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

	public Instant getRevokedAt() {
		return revokedAt;
	}

	public void setRevokedAt(Instant revokedAt) {
		this.revokedAt = revokedAt;
	}

	public UUID getReplacedById() {
		return replacedById;
	}

	public void setReplacedById(UUID replacedById) {
		this.replacedById = replacedById;
	}

	public String getUserAgent() {
		return userAgent;
	}

	public String getIp() {
		return ip;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public boolean isActive() {
		return revokedAt == null && expiresAt.isAfter(Instant.now());
	}
}
