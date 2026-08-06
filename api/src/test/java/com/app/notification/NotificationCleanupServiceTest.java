package com.app.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

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
		when(notificationRepository.deleteByCreatedAtBefore(any())).thenReturn(3);
		NotificationCleanupService service = new NotificationCleanupService(notificationRepository);

		service.expireOldNotifications();

		ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
		verify(notificationRepository).deleteByCreatedAtBefore(cutoffCaptor.capture());

		Instant expectedCutoff = Instant.now().minus(30, ChronoUnit.DAYS);
		assertThat(cutoffCaptor.getValue()).isBetween(expectedCutoff.minusSeconds(60), expectedCutoff.plusSeconds(60));
	}
}
