package com.app.tracking;

import com.app.couple.Couple;
import com.app.media.MediaType;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "media_track")
public class MediaTrack {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "couple_id", nullable = false)
	private Couple couple;

	@Column(name = "tmdb_id", nullable = false)
	private Long tmdbId;

	@Enumerated(EnumType.STRING)
	@Column(name = "media_type", nullable = false)
	private MediaType mediaType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private MediaStatus status;

	@Column(name = "watched_date")
	private LocalDate watchedDate;

	@Column
	private Integer runtime;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@OneToMany(mappedBy = "mediaTrack", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<UserReview> reviews = new ArrayList<>();

	protected MediaTrack() {
	}

	public MediaTrack(Couple couple, Long tmdbId, MediaType mediaType, MediaStatus status) {
		this.couple = couple;
		this.tmdbId = tmdbId;
		this.mediaType = mediaType;
		this.status = status;
	}

	public UUID getId() {
		return id;
	}

	public Couple getCouple() {
		return couple;
	}

	public Long getTmdbId() {
		return tmdbId;
	}

	public MediaType getMediaType() {
		return mediaType;
	}

	public MediaStatus getStatus() {
		return status;
	}

	public void setStatus(MediaStatus status) {
		this.status = status;
	}

	public LocalDate getWatchedDate() {
		return watchedDate;
	}

	public void setWatchedDate(LocalDate watchedDate) {
		this.watchedDate = watchedDate;
	}

	public Integer getRuntime() {
		return runtime;
	}

	public void setRuntime(Integer runtime) {
		this.runtime = runtime;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public List<UserReview> getReviews() {
		return reviews;
	}
}
