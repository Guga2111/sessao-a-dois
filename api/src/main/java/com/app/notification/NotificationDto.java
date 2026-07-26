package com.app.notification;

import com.app.media.MediaType;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationDto(UUID id, NotificationType type, Long tmdbId, MediaType mediaType, String title,
		UUID actorUserId, String actorName, boolean read, LocalDateTime createdAt) {
}
