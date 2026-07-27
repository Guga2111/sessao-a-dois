package com.app.tracking;

import com.app.couple.Couple;
import com.app.media.MediaDetailsService;
import com.app.notification.NotificationRepository;
import com.app.notification.NotificationService;
import com.app.notification.NotificationType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

	public RatingRequestService(NotificationService notificationService,
			NotificationRepository notificationRepository, MediaDetailsService mediaDetailsService) {
		this.notificationService = notificationService;
		this.notificationRepository = notificationRepository;
		this.mediaDetailsService = mediaDetailsService;
	}

	/**
	 * Notifica o parceiro do {@code actorUserId} de que ele ainda nao avaliou o
	 * titulo. No-op quando o ator nao deu nota, quando o casal nao tem parceiro,
	 * quando o parceiro ja tem nota, ou quando ja existe um pedido nao lido para
	 * o mesmo titulo (idempotencia).
	 */
	public void onRatingRegistered(MediaTrack track, UUID actorUserId) {
		if (ratingOf(track, actorUserId) == null) {
			return;
		}

		Couple couple = track.getCouple();
		UUID partnerId = partnerOf(couple, actorUserId);
		if (partnerId == null) {
			return;
		}

		if (ratingOf(track, partnerId) != null) {
			return;
		}

		boolean alreadyPending = notificationRepository
			.existsByRecipientUserIdAndCoupleIdAndTmdbIdAndTypeAndReadFalse(partnerId, couple.getId(),
					track.getTmdbId(), NotificationType.RATING_REQUEST);
		if (alreadyPending) {
			return;
		}

		notificationService.notifyRatingRequest(couple, partnerId, actorUserId, track.getTmdbId(),
				track.getMediaType(), resolveTitle(track), track.getId());
	}

	private UUID partnerOf(Couple couple, UUID actorUserId) {
		if (actorUserId.equals(couple.getUser1Id())) {
			return couple.getUser2Id();
		}
		if (actorUserId.equals(couple.getUser2Id())) {
			return couple.getUser1Id();
		}
		return null;
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
