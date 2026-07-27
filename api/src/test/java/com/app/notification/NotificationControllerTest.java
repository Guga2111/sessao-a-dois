package com.app.notification;

import com.app.media.MediaType;
import com.app.security.JwtService;
import com.app.security.SecurityConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class NotificationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private NotificationService notificationService;

	@MockitoBean
	private JwtService jwtService;

	private static UsernamePasswordAuthenticationToken authenticatedUser(UUID userId) {
		return new UsernamePasswordAuthenticationToken(userId, null, List.of());
	}

	private NotificationDto dto(UUID actorId) {
		return new NotificationDto(UUID.randomUUID(), NotificationType.MATCH, 603L, MediaType.MOVIE, "Matrix",
				actorId, "Ana", false, Instant.now(), UUID.randomUUID());
	}

	@Test
	void list_deniesAccessWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
	}

	@Test
	void list_returnsPagedNotifications() throws Exception {
		UUID userId = UUID.randomUUID();
		Page<NotificationDto> page = new PageImpl<>(List.of(dto(userId)));
		when(notificationService.listNotifications(userId, 0, 20)).thenReturn(page);

		mockMvc.perform(get("/api/notifications").with(authentication(authenticatedUser(userId))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].title").value("Matrix"));
	}

	@Test
	void unreadCount_deniesAccessWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/notifications/unread-count")).andExpect(status().isUnauthorized());
	}

	@Test
	void unreadCount_returnsCount() throws Exception {
		UUID userId = UUID.randomUUID();
		when(notificationService.unreadCount(userId)).thenReturn(3L);

		mockMvc.perform(get("/api/notifications/unread-count").with(authentication(authenticatedUser(userId))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.count").value(3));
	}

	@Test
	void markAsRead_deniesAccessWithoutAuthentication() throws Exception {
		mockMvc.perform(patch("/api/notifications/{id}/read", UUID.randomUUID()))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void markAsRead_returnsOkOnSuccess() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID notificationId = UUID.randomUUID();
		doNothing().when(notificationService).markAsRead(notificationId, userId);

		mockMvc
			.perform(patch("/api/notifications/{id}/read", notificationId)
				.with(authentication(authenticatedUser(userId))))
			.andExpect(status().isOk());
	}

	@Test
	void markAsRead_returnsForbiddenWhenNotificationBelongsToAnotherUser() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID notificationId = UUID.randomUUID();
		doThrow(new AccessDeniedException("notificacao nao pertence ao usuario autenticado")).when(notificationService)
			.markAsRead(eq(notificationId), eq(userId));

		mockMvc
			.perform(patch("/api/notifications/{id}/read", notificationId)
				.with(authentication(authenticatedUser(userId))))
			.andExpect(status().isForbidden());
	}

	@Test
	void markAllAsRead_deniesAccessWithoutAuthentication() throws Exception {
		mockMvc.perform(patch("/api/notifications/read-all")).andExpect(status().isUnauthorized());
	}

	@Test
	void markAllAsRead_returnsOkOnSuccess() throws Exception {
		UUID userId = UUID.randomUUID();
		doNothing().when(notificationService).markAllAsRead(userId);

		mockMvc.perform(patch("/api/notifications/read-all").with(authentication(authenticatedUser(userId))))
			.andExpect(status().isOk());
	}
}
