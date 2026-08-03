package com.app.match;

import com.app.couple.Couple;
import com.app.couple.CoupleRepository;
import com.app.media.MediaDetails;
import com.app.media.MediaDetailsService;
import com.app.notification.NotificationService;
import com.app.notification.NotificationType;
import com.app.tracking.MediaStatus;
import com.app.tracking.MediaTrack;
import com.app.tracking.MediaTrackRepository;
import com.app.tracking.ResourceNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class MatchService {

	private static final Logger log = LoggerFactory.getLogger(MatchService.class);

	private final MatchLikeRepository matchLikeRepository;
	private final MatchRejectRepository matchRejectRepository;
	private final MediaTrackRepository mediaTrackRepository;
	private final CoupleRepository coupleRepository;
	private final MediaDetailsService mediaDetailsService;
	private final SimpMessagingTemplate messagingTemplate;
	private final NotificationService notificationService;

	public MatchService(MatchLikeRepository matchLikeRepository, MatchRejectRepository matchRejectRepository,
			MediaTrackRepository mediaTrackRepository, CoupleRepository coupleRepository,
			MediaDetailsService mediaDetailsService, SimpMessagingTemplate messagingTemplate,
			NotificationService notificationService) {
		this.matchLikeRepository = matchLikeRepository;
		this.matchRejectRepository = matchRejectRepository;
		this.mediaTrackRepository = mediaTrackRepository;
		this.coupleRepository = coupleRepository;
		this.mediaDetailsService = mediaDetailsService;
		this.messagingTemplate = messagingTemplate;
		this.notificationService = notificationService;
	}

	@Transactional
	public LikeResponse like(UUID coupleId, UUID userId, LikeRequest request) {
		if (mediaTrackRepository.existsByCoupleIdAndTmdbId(coupleId, request.tmdbId())) {
			throw new TitleAlreadyTrackedException();
		}

		Couple couple = null;
		if (matchLikeRepository.findByCoupleIdAndUserIdAndTmdbId(coupleId, userId, request.tmdbId()).isEmpty()) {
			couple = coupleRepository.findById(coupleId)
				.orElseThrow(() -> new ResourceNotFoundException("casal nao encontrado"));

			MatchLike like = new MatchLike(couple, userId, request.tmdbId(), request.mediaType());
			like.setTitle(request.title());
			like.setPosterUrl(request.posterUrl());
			like.setReleaseYear(request.releaseYear());
			matchLikeRepository.save(like);
		}

		boolean matched = matchLikeRepository
			.findFirstByCoupleIdAndTmdbIdAndUserIdNot(coupleId, request.tmdbId(), userId)
			.isPresent();

		if (matched && !mediaTrackRepository.existsByCoupleIdAndTmdbId(coupleId, request.tmdbId())) {
			if (couple == null) {
				couple = coupleRepository.findById(coupleId)
					.orElseThrow(() -> new ResourceNotFoundException("casal nao encontrado"));
			}
			createMatch(couple, request, userId);
		}

		return new LikeResponse(matched);
	}

	@Transactional
	public void reject(UUID coupleId, UUID userId, LikeRequest request) {
		if (matchRejectRepository.findByCoupleIdAndUserIdAndTmdbId(coupleId, userId, request.tmdbId()).isPresent()) {
			return;
		}
		Couple couple = coupleRepository.findById(coupleId)
			.orElseThrow(() -> new ResourceNotFoundException("casal nao encontrado"));
		matchRejectRepository.save(new MatchReject(couple, userId, request.tmdbId(), request.mediaType()));

		boolean partnerLiked = matchLikeRepository
			.findFirstByCoupleIdAndTmdbIdAndUserIdNot(coupleId, request.tmdbId(), userId)
			.isPresent();
		if (partnerLiked) {
			MediaDetails details = mediaDetailsService.getDetails(request.mediaType(), request.tmdbId());
			notificationService.notifyCouple(couple, NotificationType.NO_MATCH, request.tmdbId(), request.mediaType(),
					details.title(), userId);
		}
	}

	@Transactional
	public List<PendingMatchDto> getPending(UUID coupleId, UUID userId) {
		var page = matchLikeRepository.findPendingForUser(coupleId, userId, PageRequest.of(0, 10));
		List<PendingMatchDto> result = new ArrayList<>();
		for (MatchLike ml : page.getContent()) {
			healMetadata(ml);
			result.add(new PendingMatchDto(ml.getTmdbId(), ml.getMediaType(), ml.getTitle(), ml.getPosterUrl(),
					ml.getReleaseYear()));
		}
		return result;
	}

	/**
	 * Self-heals a pre-V4 row (title null) by fetching TMDB once and persisting the result, so
	 * every read after this one skips TMDB entirely for this row. A TMDB failure leaves the
	 * fields null instead of failing the request - the row is retried on the next read.
	 */
	private void healMetadata(MatchLike ml) {
		if (ml.getTitle() != null) {
			return;
		}
		try {
			MediaDetails details = mediaDetailsService.getDetails(ml.getMediaType(), ml.getTmdbId());
			ml.setTitle(details.title());
			ml.setPosterUrl(details.posterUrl());
			ml.setReleaseYear(details.year());
			matchLikeRepository.save(ml);
		}
		catch (RuntimeException ex) {
			log.warn("Nao foi possivel auto-curar os metadados do like {} no TMDB: {}", ml.getTmdbId(),
					ex.getMessage());
		}
	}

	private void createMatch(Couple couple, LikeRequest request, UUID actorUserId) {
		MediaDetails details = mediaDetailsService.getDetails(request.mediaType(), request.tmdbId());

		MediaTrack track = new MediaTrack(couple, request.tmdbId(), request.mediaType(), MediaStatus.WANT_TO_SEE);
		track.setGenreIds(details.genreIds());
		track.setTitle(details.title());
		track.setPosterUrl(details.posterUrl());
		track.setReleaseYear(details.year());
		mediaTrackRepository.save(track);

		MatchEvent event = new MatchEvent(request.tmdbId(), details.title(), request.mediaType());
		messagingTemplate.convertAndSend("/topic/couple/" + couple.getId() + "/match", event);

		notificationService.notifyCouple(couple, NotificationType.MATCH, request.tmdbId(), request.mediaType(),
				details.title(), actorUserId);
	}
}
