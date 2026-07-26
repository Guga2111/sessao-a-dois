package com.app.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationCleanupServiceTest {

	@Mock
	private NotificationRepository notificationRepository;

	@Test
	void expireOldNotificationsDeletesByThirtyDayCutoff() {
		when(notificationRepository.deleteByCreatedAtBefore(any())).thenReturn(3L);
		NotificationCleanupService service = new NotificationCleanupService(notificationRepository);

		service.expireOldNotifications();

		ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
		verify(notificationRepository).deleteByCreatedAtBefore(cutoffCaptor.capture());

		LocalDateTime expectedCutoff = LocalDateTime.now().minusDays(30);
		assertThat(cutoffCaptor.getValue()).isBetween(expectedCutoff.minusMinutes(1), expectedCutoff.plusMinutes(1));
	}
}
