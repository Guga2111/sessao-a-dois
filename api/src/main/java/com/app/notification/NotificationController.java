package com.app.notification;

import com.app.common.PageResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

	private final NotificationService notificationService;

	public NotificationController(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@GetMapping
	public ResponseEntity<PageResponse<NotificationDto>> list(@AuthenticationPrincipal UUID userId,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(PageResponse.from(notificationService.listNotifications(userId, page, size)));
	}

	@GetMapping("/unread-count")
	public ResponseEntity<UnreadCountResponse> unreadCount(@AuthenticationPrincipal UUID userId) {
		return ResponseEntity.ok(new UnreadCountResponse(notificationService.unreadCount(userId)));
	}

	@PatchMapping("/{id}/read")
	public ResponseEntity<Void> markAsRead(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
		notificationService.markAsRead(id, userId);
		return ResponseEntity.ok().build();
	}

	@PatchMapping("/read-all")
	public ResponseEntity<Void> markAllAsRead(@AuthenticationPrincipal UUID userId) {
		notificationService.markAllAsRead(userId);
		return ResponseEntity.ok().build();
	}
}
