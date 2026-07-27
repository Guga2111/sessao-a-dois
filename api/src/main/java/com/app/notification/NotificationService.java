package com.app.notification;

import com.app.couple.Couple;
import com.app.media.MediaType;
import com.app.tracking.ResourceNotFoundException;
import com.app.user.User;
import com.app.user.UserRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

	/**
	 * Cria uma unica notificacao de pedido de avaliacao para o parceiro do ator.
	 * Diferente de {@link #notifyCouple}, nao gera uma notificacao por membro:
	 * quem acabou de avaliar nao precisa ser lembrado. Se o casal ainda nao tem
	 * parceiro ({@code recipientUserId} nulo), nada e persistido nem publicado.
	 */
	public Optional<NotificationDto> notifyRatingRequest(Couple couple, UUID recipientUserId, UUID actorUserId,
			Long tmdbId, MediaType mediaType, String title, UUID mediaTrackId) {
		if (recipientUserId == null) {
			return Optional.empty();
		}

		String actorName = userRepository.findById(actorUserId).map(User::getName).orElse(null);
		Notification saved = notificationRepository.save(new Notification(couple, recipientUserId,
				NotificationType.RATING_REQUEST, tmdbId, mediaType, title, actorUserId, mediaTrackId));
		NotificationDto dto = toDto(saved, actorName);

		scheduleBroadcast(String.format(DESTINATION_TEMPLATE, couple.getId()), List.of(dto));
		return Optional.of(dto);
	}

	public Page<NotificationDto> listNotifications(UUID recipientUserId, int page, int size) {
		Page<Notification> notifications = notificationRepository
			.findByRecipientUserIdOrderByCreatedAtDesc(recipientUserId, PageRequest.of(page, size));

		Map<UUID, String> actorNames = resolveActorNames(notifications.getContent());
		return notifications.map(n -> toDto(n, actorNames.get(n.getActorUserId())));
	}

	public long unreadCount(UUID recipientUserId) {
		return notificationRepository.countByRecipientUserIdAndReadFalse(recipientUserId);
	}

	@Transactional
	public void markAsRead(UUID notificationId, UUID recipientUserId) {
		Notification notification = notificationRepository.findById(notificationId)
			.orElseThrow(() -> new ResourceNotFoundException("notificacao nao encontrada"));

		if (!notification.getRecipientUserId().equals(recipientUserId)) {
			throw new AccessDeniedException("notificacao nao pertence ao usuario autenticado");
		}

		notification.setRead(true);
		notificationRepository.save(notification);
	}

	@Transactional
	public void markAllAsRead(UUID recipientUserId) {
		notificationRepository.markAllAsReadByRecipientUserId(recipientUserId);
	}

	private Map<UUID, String> resolveActorNames(List<Notification> notifications) {
		List<UUID> actorIds = notifications.stream().map(Notification::getActorUserId).distinct().toList();

		Map<UUID, String> names = new HashMap<>();
		for (User user : userRepository.findAllById(actorIds)) {
			names.put(user.getId(), user.getName());
		}
		return names;
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
				notification.isRead(), notification.getCreatedAt(), notification.getRecipientUserId(),
				notification.getMediaTrackId());
	}
}
