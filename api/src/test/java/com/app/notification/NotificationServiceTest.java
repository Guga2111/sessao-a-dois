package com.app.notification;

import com.app.couple.Couple;
import com.app.media.MediaType;
import com.app.user.User;
import com.app.user.UserRepository;

import com.app.common.ResourceNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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

	@Test
	void notifyRatingRequest_createsSingleNotificationForThePartner() {
		UUID coupleId = UUID.randomUUID();
		UUID actorId = UUID.randomUUID();
		UUID partnerId = UUID.randomUUID();
		UUID mediaTrackId = UUID.randomUUID();
		Couple couple = couple(coupleId, actorId, partnerId);

		when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
			Notification n = inv.getArgument(0);
			ReflectionTestUtils.setField(n, "id", UUID.randomUUID());
			return n;
		});
		when(userRepository.findById(actorId)).thenReturn(Optional.of(new User("Ana", "ana@x.com", "hash")));

		Optional<NotificationDto> dto = notificationService.notifyRatingRequest(couple, partnerId, actorId, 603L,
				MediaType.MOVIE, "Matrix", mediaTrackId);

		assertThat(dto).isPresent();
		assertThat(dto.get().type()).isEqualTo(NotificationType.RATING_REQUEST);
		assertThat(dto.get().recipientUserId()).isEqualTo(partnerId);
		assertThat(dto.get().mediaTrackId()).isEqualTo(mediaTrackId);
		assertThat(dto.get().actorName()).isEqualTo("Ana");

		ArgumentCaptor<Notification> savedCaptor = ArgumentCaptor.forClass(Notification.class);
		verify(notificationRepository, times(1)).save(savedCaptor.capture());
		assertThat(savedCaptor.getValue().getRecipientUserId()).isEqualTo(partnerId);
		verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/couple/" + coupleId + "/notifications"),
				any(Object.class));
	}

	@Test
	void notifyRatingRequest_pushesOnlyAfterCommitWhenTransactionIsActive() {
		UUID coupleId = UUID.randomUUID();
		UUID actorId = UUID.randomUUID();
		UUID partnerId = UUID.randomUUID();
		Couple couple = couple(coupleId, actorId, partnerId);

		when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
			Notification n = inv.getArgument(0);
			ReflectionTestUtils.setField(n, "id", UUID.randomUUID());
			return n;
		});
		when(userRepository.findById(actorId)).thenReturn(Optional.empty());

		TransactionSynchronizationManager.initSynchronization();
		try {
			notificationService.notifyRatingRequest(couple, partnerId, actorId, 603L, MediaType.MOVIE, "Matrix",
					UUID.randomUUID());

			verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));

			TransactionSynchronizationManager.getSynchronizations()
				.forEach(TransactionSynchronization::afterCommit);
		}
		finally {
			TransactionSynchronizationManager.clearSynchronization();
		}

		verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/couple/" + coupleId + "/notifications"),
				any(Object.class));
	}

	@Test
	void notifyRatingRequest_doesNothingWhenCoupleHasNoPartner() {
		Couple couple = couple(UUID.randomUUID(), UUID.randomUUID(), null);

		Optional<NotificationDto> dto = notificationService.notifyRatingRequest(couple, null, couple.getUser1Id(), 603L,
				MediaType.MOVIE, "Matrix", UUID.randomUUID());

		assertThat(dto).isEmpty();
		verify(notificationRepository, never()).save(any(Notification.class));
		verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
	}

	@Test
	void listNotifications_returnsPageWithResolvedActorNames() {
		UUID coupleId = UUID.randomUUID();
		UUID recipientId = UUID.randomUUID();
		UUID actorId = UUID.randomUUID();
		Couple couple = couple(coupleId, recipientId, actorId);
		Notification notification = savedNotification(couple, recipientId, actorId);

		when(notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(recipientId, PageRequest.of(0, 20)))
			.thenReturn(new PageImpl<>(List.of(notification)));
		User actor = new User("Ana", "ana@x.com", "hash");
		ReflectionTestUtils.setField(actor, "id", actorId);
		when(userRepository.findAllById(List.of(actorId))).thenReturn(List.of(actor));

		Page<NotificationDto> page = notificationService.listNotifications(recipientId, 0, 20);

		assertThat(page.getContent()).hasSize(1);
		assertThat(page.getContent().get(0).actorName()).isEqualTo("Ana");
	}

	@Test
	void listNotifications_mapsMediaTrackIdForRatingRequest() {
		UUID recipientId = UUID.randomUUID();
		UUID actorId = UUID.randomUUID();
		UUID mediaTrackId = UUID.randomUUID();
		Couple couple = couple(UUID.randomUUID(), recipientId, actorId);
		Notification notification = new Notification(couple, recipientId, NotificationType.RATING_REQUEST, 603L,
				MediaType.MOVIE, "Matrix", actorId, mediaTrackId);
		ReflectionTestUtils.setField(notification, "id", UUID.randomUUID());

		when(notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(recipientId, PageRequest.of(0, 20)))
			.thenReturn(new PageImpl<>(List.of(notification)));
		when(userRepository.findAllById(List.of(actorId))).thenReturn(List.of());

		NotificationDto dto = notificationService.listNotifications(recipientId, 0, 20).getContent().get(0);

		assertThat(dto.type()).isEqualTo(NotificationType.RATING_REQUEST);
		assertThat(dto.mediaTrackId()).isEqualTo(mediaTrackId);
	}

	@Test
	void listNotifications_mediaTrackIdIsNullForMatchNotifications() {
		UUID recipientId = UUID.randomUUID();
		UUID actorId = UUID.randomUUID();
		Couple couple = couple(UUID.randomUUID(), recipientId, actorId);
		Notification notification = savedNotification(couple, recipientId, actorId);

		when(notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(recipientId, PageRequest.of(0, 20)))
			.thenReturn(new PageImpl<>(List.of(notification)));
		when(userRepository.findAllById(List.of(actorId))).thenReturn(List.of());

		NotificationDto dto = notificationService.listNotifications(recipientId, 0, 20).getContent().get(0);

		assertThat(dto.type()).isEqualTo(NotificationType.MATCH);
		assertThat(dto.mediaTrackId()).isNull();
	}

	@Test
	void unreadCount_delegatesToRepository() {
		UUID recipientId = UUID.randomUUID();
		when(notificationRepository.countByRecipientUserIdAndReadFalse(recipientId)).thenReturn(5L);

		assertThat(notificationService.unreadCount(recipientId)).isEqualTo(5L);
	}

	@Test
	void markAsRead_marksOwnedNotificationAsRead() {
		UUID recipientId = UUID.randomUUID();
		Notification notification = savedNotification(couple(UUID.randomUUID(), recipientId, UUID.randomUUID()),
				recipientId, UUID.randomUUID());
		when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));

		notificationService.markAsRead(notification.getId(), recipientId);

		assertThat(notification.isRead()).isTrue();
		verify(notificationRepository).save(notification);
	}

	@Test
	void markAsRead_throwsResourceNotFoundWhenMissing() {
		UUID notificationId = UUID.randomUUID();
		when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> notificationService.markAsRead(notificationId, UUID.randomUUID()))
			.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void markAsRead_throwsAccessDeniedWhenNotOwnedByUser() {
		UUID recipientId = UUID.randomUUID();
		UUID otherUserId = UUID.randomUUID();
		Notification notification = savedNotification(couple(UUID.randomUUID(), recipientId, UUID.randomUUID()),
				recipientId, UUID.randomUUID());
		when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));

		assertThatThrownBy(() -> notificationService.markAsRead(notification.getId(), otherUserId))
			.isInstanceOf(AccessDeniedException.class);
		verify(notificationRepository, never()).save(any(Notification.class));
	}

	@Test
	void markAllAsRead_delegatesToRepository() {
		UUID recipientId = UUID.randomUUID();

		notificationService.markAllAsRead(recipientId);

		verify(notificationRepository).markAllAsReadByRecipientUserId(recipientId);
	}
}
