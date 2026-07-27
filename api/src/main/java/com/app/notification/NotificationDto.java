package com.app.notification;

import com.app.media.MediaType;

import java.time.Instant;
import java.util.UUID;

public record NotificationDto(UUID id, NotificationType type, Long tmdbId, MediaType mediaType, String title,
		UUID actorUserId, String actorName, boolean read, Instant createdAt, UUID recipientUserId) {
}
