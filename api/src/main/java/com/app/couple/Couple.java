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

	@Column(name = "dissolved_at")
	private Instant dissolvedAt;

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

	/** Substitui o codigo de convite e sua expiracao (US-009) - o codigo antigo deixa de funcionar imediatamente. */
	public void regenerateInviteCode(String inviteCode, Instant inviteCodeExpiresAt) {
		this.inviteCode = inviteCode;
		this.inviteCodeExpiresAt = inviteCodeExpiresAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getDissolvedAt() {
		return dissolvedAt;
	}

	/** Um casal so e "ativo" enquanto nao foi dissolvido - e o unico predicado que define isso. */
	public boolean isActive() {
		return dissolvedAt == null;
	}

	/**
	 * Desfaz o vinculo do casal (epico 9, US-001), preservando a linha e todo o historico ligado a ela
	 * (D13: dissolver, nao apagar). Alem de marcar o instante, limpa o codigo de convite (E9.17) - senao
	 * um casal criado e nunca pareado continuaria com codigo valido depois de morto, e um parceiro novo
	 * entraria num casal dissolvido pelo caminho do convite, que nao parte do usuario.
	 *
	 * Chamar num casal ja dissolvido lanca (E9.7): o metodo protege o proprio invariante. Na pratica o
	 * endpoint nunca chega aqui (casal dissolvido ja nao e "ativo" e vira 404 antes), entao a excecao e
	 * rede contra bug interno.
	 */
	public void dissolve() {
		if (dissolvedAt != null) {
			throw new CoupleAlreadyDissolvedException(id);
		}
		this.dissolvedAt = Instant.now();
		clearInviteCode();
	}
}
