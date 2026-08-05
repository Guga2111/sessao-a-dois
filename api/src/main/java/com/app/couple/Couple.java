package com.app.couple;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "couples", uniqueConstraints = @UniqueConstraint(columnNames = "invite_code"))
public class Couple {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user1_id", nullable = false)
	private UUID user1Id;

	@Column(name = "user2_id")
	private UUID user2Id;

	@Column(name = "invite_code", unique = true)
	private String inviteCode;

	@Column(name = "invite_code_expires_at")
	private Instant inviteCodeExpiresAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Couple() {
	}

	public Couple(UUID user1Id, String inviteCode) {
		this(user1Id, inviteCode, null);
	}

	public Couple(UUID user1Id, String inviteCode, Instant inviteCodeExpiresAt) {
		this.user1Id = user1Id;
		this.inviteCode = inviteCode;
		this.inviteCodeExpiresAt = inviteCodeExpiresAt;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getUser1Id() {
		return user1Id;
	}

	public UUID getUser2Id() {
		return user2Id;
	}

	public void setUser2Id(UUID user2Id) {
		this.user2Id = user2Id;
	}

	public String getInviteCode() {
		return inviteCode;
	}

	public Instant getInviteCodeExpiresAt() {
		return inviteCodeExpiresAt;
	}

	/** Limpa o codigo de convite apos o uso (US-008), para que nenhuma tentativa posterior com ele tenha efeito. */
	public void clearInviteCode() {
		this.inviteCode = null;
		this.inviteCodeExpiresAt = null;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
