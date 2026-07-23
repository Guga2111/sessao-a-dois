package com.app.match;

import com.app.couple.Couple;
import com.app.media.MediaType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "match_reject", uniqueConstraints = @UniqueConstraint(columnNames = {"couple_id", "user_id", "tmdb_id"}))
public class MatchReject {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "couple_id", nullable = false)
	private Couple couple;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "tmdb_id", nullable = false)
	private Long tmdbId;

	@Enumerated(EnumType.STRING)
	@Column(name = "media_type", nullable = false)
	private MediaType mediaType;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected MatchReject() {
	}

	public MatchReject(Couple couple, UUID userId, Long tmdbId, MediaType mediaType) {
		this.couple = couple;
		this.userId = userId;
		this.tmdbId = tmdbId;
		this.mediaType = mediaType;
	}

	public UUID getId() {
		return id;
	}

	public Couple getCouple() {
		return couple;
	}

	public UUID getUserId() {
		return userId;
	}

	public Long getTmdbId() {
		return tmdbId;
	}

	public MediaType getMediaType() {
		return mediaType;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
