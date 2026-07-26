package com.app.notification;

import com.app.couple.Couple;
import com.app.media.MediaType;
import com.app.user.User;
import com.app.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

	@Mock
	private NotificationRepository notificationRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private SimpMessagingTemplate messagingTemplate;

	private NotificationService notificationService;

	@BeforeEach
	void setUp() {
		notificationService = new NotificationService(notificationRepository, userRepository, messagingTemplate);
	}

	private Couple couple(UUID coupleId, UUID user1Id, UUID user2Id) {
		Couple couple = new Couple(user1Id, "ABC234");
		ReflectionTestUtils.setField(couple, "id", coupleId);
		couple.setUser2Id(user2Id);
		return couple;
	}

	private Notification savedNotification(Couple couple, UUID recipientId, UUID actorId) {
		Notification n = new Notification(couple, recipientId, NotificationType.MATCH, 603L, MediaType.MOVIE,
				"Matrix", actorId);
		ReflectionTestUtils.setField(n, "id", UUID.randomUUID());
		return n;
	}

	@Test
	void notifyCouple_createsOneNotificationPerCoupleMemberAndBroadcastsAfterCommit() {
		UUID coupleId = UUID.randomUUID();
		UUID user1Id = UUID.randomUUID();
		UUID user2Id = UUID.randomUUID();
		Couple couple = couple(coupleId, user1Id, user2Id);

		when(notificationRepository.save(any(Notification.class)))
			.thenAnswer(inv -> savedNotification(couple, ((Notification) inv.getArgument(0)).getRecipientUserId(),
					user1Id));
		when(userRepository.findById(user1Id)).thenReturn(Optional.of(new User("Ana", "ana@x.com", "hash")));

		List<NotificationDto> dtos = notificationService.notifyCouple(couple, NotificationType.MATCH, 603L,
				MediaType.MOVIE, "Matrix", user1Id);

		assertThat(dtos).hasSize(2);
		assertThat(dtos).allMatch(dto -> dto.type() == NotificationType.MATCH);
		assertThat(dtos).allMatch(dto -> dto.actorName().equals("Ana"));
		verify(notificationRepository, times(2)).save(any(Notification.class));

		ArgumentCaptor<NotificationDto> payloadCaptor = ArgumentCaptor.forClass(NotificationDto.class);
		verify(messagingTemplate, times(2)).convertAndSend(eq("/topic/couple/" + coupleId + "/notifications"),
				payloadCaptor.capture());
		assertThat(payloadCaptor.getAllValues()).hasSize(2);
	}

	@Test
	void notifyCouple_actorNameNullWhenActorNotFound() {
		UUID coupleId = UUID.randomUUID();
		UUID user1Id = UUID.randomUUID();
		UUID user2Id = UUID.randomUUID();
		Couple couple = couple(coupleId, user1Id, user2Id);

		when(notificationRepository.save(any(Notification.class)))
			.thenAnswer(inv -> savedNotification(couple, ((Notification) inv.getArgument(0)).getRecipientUserId(),
					user1Id));
		when(userRepository.findById(user1Id)).thenReturn(Optional.empty());

		List<NotificationDto> dtos = notificationService.notifyCouple(couple, NotificationType.NO_MATCH, 603L,
				MediaType.MOVIE, "Matrix", user1Id);

		assertThat(dtos).allMatch(dto -> dto.actorName() == null);
	}
}
