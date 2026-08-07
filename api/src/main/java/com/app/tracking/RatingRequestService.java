package com.app.tracking;

import com.app.couple.CoupleFacade;
import com.app.media.MediaDetailsService;
import com.app.notification.Notification;
import com.app.notification.NotificationRepository;
import com.app.notification.NotificationService;
import com.app.notification.NotificationType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Orquestrador unico do pedido de avaliacao: chamado sempre que uma nota e
 * registrada (markAsWatched, addTrack com WATCHED e upsertReview), decide se o
 * parceiro ainda precisa avaliar e, em caso positivo, cria a notificacao
 * RATING_REQUEST. Roda dentro da transacao de quem chama.
 */
@Service
public class RatingRequestService {

	private static final Logger log = LoggerFactory.getLogger(RatingRequestService.class);

	/** Usado quando o TMDB nao responde: a notificacao nao pode depender dele. */
	static final String FALLBACK_TITLE = "seu titulo";

	private final NotificationService notificationService;
	private final NotificationRepository notificationRepository;
	private final MediaDetailsService mediaDetailsService;
	private final CoupleFacade coupleFacade;

	public RatingRequestService(NotificationService notificationService,
			NotificationRepository notificationRepository, MediaDetailsService mediaDetailsService,
			CoupleFacade coupleFacade) {
		this.notificationService = notificationService;
		this.notificationRepository = notificationRepository;
		this.mediaDetailsService = mediaDetailsService;
		this.coupleFacade = coupleFacade;
	}

	/**
	 * Resolve os pedidos pendentes do proprio {@code actorUserId} para o titulo e
	 * notifica o parceiro de que ele ainda nao avaliou. No-op quando o ator nao
	 * deu nota, quando o casal nao tem parceiro, quando o parceiro ja tem nota, ou
	 * quando ja existe um pedido nao lido para o mesmo titulo (idempotencia).
	 */
	public void onRatingRegistered(MediaTrack track, UUID actorUserId) {
		if (ratingOf(track, actorUserId) == null) {
			return;
		}

		UUID coupleId = track.getCoupleId();
		resolvePendingRequestsFor(coupleId, actorUserId, track.getTmdbId());

		// Uma unica resolucao de membros por operacao: com couple_id sendo UUID, achar o parceiro
		// dentro do laco de notificacoes custaria uma consulta por notificacao (regressao da T2.2).
		UUID partnerId = partnerOf(coupleFacade.memberIds(coupleId), actorUserId);
		if (partnerId == null) {
			return;
		}

		if (ratingOf(track, partnerId) != null) {
			return;
		}

		boolean alreadyPending = notificationRepository
			.existsByRecipientUserIdAndCoupleIdAndTmdbIdAndTypeAndReadFalse(partnerId, coupleId,
					track.getTmdbId(), NotificationType.RATING_REQUEST);
		if (alreadyPending) {
			return;
		}

		notificationService.notifyRatingRequest(coupleId, partnerId, actorUserId, track.getTmdbId(),
				track.getMediaType(), resolveTitle(track), track.getId());
	}

	/**
	 * Remove os pedidos de avaliacao do titulo quando ele sai da lista do casal:
	 * sem isso a notificacao continuaria apontando para um track inexistente.
	 * Roda na transacao de {@code MediaTrackService.deleteTrack}, antes da remocao.
	 */
	public void onTrackDeleted(MediaTrack track) {
		notificationRepository.deleteByCoupleIdAndTmdbIdAndType(track.getCoupleId(), track.getTmdbId(),
				NotificationType.RATING_REQUEST);
	}

	/**
	 * Ao dar a propria nota, o ator resolve sozinho qualquer RATING_REQUEST que
	 * tenha recebido para aquele titulo — sem acao manual no dropdown.
	 */
	private void resolvePendingRequestsFor(UUID coupleId, UUID actorUserId, Long tmdbId) {
		List<Notification> pending = notificationRepository
			.findByRecipientUserIdAndCoupleIdAndTmdbIdAndTypeAndReadFalse(actorUserId, coupleId, tmdbId,
					NotificationType.RATING_REQUEST);
		if (pending.isEmpty()) {
			return;
		}
		pending.forEach(notification -> notification.setRead(true));
		notificationRepository.saveAll(pending);
	}

	/**
	 * O outro membro do casal, ou {@code null} quando o casal ainda nao tem parceiro — ou quando o
	 * ator nem pertence a ele, caso em que ninguem deve ser notificado.
	 */
	private UUID partnerOf(List<UUID> memberIds, UUID actorUserId) {
		if (!memberIds.contains(actorUserId)) {
			return null;
		}
		return memberIds.stream()
			.filter(memberId -> !memberId.equals(actorUserId))
			.findFirst()
			.orElse(null);
	}

	private Integer ratingOf(MediaTrack track, UUID userId) {
		return track.getReviews().stream()
			.filter(review -> review.getUser() != null && userId.equals(review.getUser().getId()))
			.findFirst()
			.map(UserReview::getRating)
			.orElse(null);
	}

	private String resolveTitle(MediaTrack track) {
		try {
			String title = mediaDetailsService.getDetails(track.getMediaType(), track.getTmdbId()).title();
			return (title == null || title.isBlank()) ? FALLBACK_TITLE : title;
		}
		catch (RuntimeException ex) {
			log.warn("Nao foi possivel obter o titulo {} no TMDB para o pedido de avaliacao: {}",
					track.getTmdbId(), ex.getMessage());
			return FALLBACK_TITLE;
		}
	}
}
