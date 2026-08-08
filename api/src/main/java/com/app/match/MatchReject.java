package com.app.match;

import com.app.media.MediaType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

	@Column(name = "couple_id", nullable = false)
	private UUID coupleId;

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

	public MatchReject(UUID coupleId, UUID userId, Long tmdbId, MediaType mediaType) {
		this.coupleId = coupleId;
		this.userId = userId;
		this.tmdbId = tmdbId;
		this.mediaType = mediaType;
	}

	public UUID getId() {
		return id;
	}

	public UUID getCoupleId() {
		return coupleId;
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
