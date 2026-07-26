package com.app.notification;

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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification", indexes = @Index(name = "idx_notification_recipient_read",
		columnList = "recipient_user_id, is_read"))
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "couple_id", nullable = false)
	private Couple couple;

	@Column(name = "recipient_user_id", nullable = false)
	private UUID recipientUserId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private NotificationType type;

	@Column(name = "tmdb_id", nullable = false)
	private Long tmdbId;

	@Enumerated(EnumType.STRING)
	@Column(name = "media_type", nullable = false)
	private MediaType mediaType;

	@Column(nullable = false)
	private String title;

	@Column(name = "actor_user_id", nullable = false)
	private UUID actorUserId;

	@Column(name = "is_read", nullable = false)
	private boolean read = false;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected Notification() {
	}

	public Notification(Couple couple, UUID recipientUserId, NotificationType type, Long tmdbId,
			MediaType mediaType, String title, UUID actorUserId) {
		this.couple = couple;
		this.recipientUserId = recipientUserId;
		this.type = type;
		this.tmdbId = tmdbId;
		this.mediaType = mediaType;
		this.title = title;
		this.actorUserId = actorUserId;
	}

	public UUID getId() {
		return id;
	}

	public Couple getCouple() {
		return couple;
	}

	public UUID getRecipientUserId() {
		return recipientUserId;
	}

	public NotificationType getType() {
		return type;
	}

	public Long getTmdbId() {
		return tmdbId;
	}

	public MediaType getMediaType() {
		return mediaType;
	}

	public String getTitle() {
		return title;
	}

	public UUID getActorUserId() {
		return actorUserId;
	}

	public boolean isRead() {
		return read;
	}

	public void setRead(boolean read) {
		this.read = read;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
