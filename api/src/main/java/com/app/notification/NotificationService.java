package com.app.notification;

import com.app.couple.Couple;
import com.app.media.MediaType;
import com.app.user.User;
import com.app.user.UserRepository;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cria as notificacoes de um evento do casal (uma por membro) e emite o
 * evento STOMP correspondente somente apos o commit da transacao corrente,
 * garantindo que a notificacao ja exista no banco quando o push chegar ao
 * cliente. Se nao houver transacao ativa, emite imediatamente.
 */
@Service
public class NotificationService {

	private static final String DESTINATION_TEMPLATE = "/topic/couple/%s/notifications";

	private final NotificationRepository notificationRepository;
	private final UserRepository userRepository;
	private final SimpMessagingTemplate messagingTemplate;

	public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository,
			SimpMessagingTemplate messagingTemplate) {
		this.notificationRepository = notificationRepository;
		this.userRepository = userRepository;
		this.messagingTemplate = messagingTemplate;
	}

	public List<NotificationDto> notifyCouple(Couple couple, NotificationType type, Long tmdbId,
			MediaType mediaType, String title, UUID actorUserId) {
		List<UUID> recipients = new ArrayList<>();
		if (couple.getUser1Id() != null) {
			recipients.add(couple.getUser1Id());
		}
		if (couple.getUser2Id() != null) {
			recipients.add(couple.getUser2Id());
		}

		String actorName = userRepository.findById(actorUserId).map(User::getName).orElse(null);
		String destination = String.format(DESTINATION_TEMPLATE, couple.getId());

		List<NotificationDto> dtos = new ArrayList<>();
		for (UUID recipientId : recipients) {
			Notification saved = notificationRepository
				.save(new Notification(couple, recipientId, type, tmdbId, mediaType, title, actorUserId));
			dtos.add(toDto(saved, actorName));
		}

		scheduleBroadcast(destination, dtos);
		return dtos;
	}

	private void scheduleBroadcast(String destination, List<NotificationDto> dtos) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					broadcast(destination, dtos);
				}
			});
		} else {
			broadcast(destination, dtos);
		}
	}

	private void broadcast(String destination, List<NotificationDto> dtos) {
		for (NotificationDto dto : dtos) {
			messagingTemplate.convertAndSend(destination, dto);
		}
	}

	private NotificationDto toDto(Notification notification, String actorName) {
		return new NotificationDto(notification.getId(), notification.getType(), notification.getTmdbId(),
				notification.getMediaType(), notification.getTitle(), notification.getActorUserId(), actorName,
				notification.isRead(), notification.getCreatedAt());
	}
}
