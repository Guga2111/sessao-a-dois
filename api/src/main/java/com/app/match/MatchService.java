package com.app.match;

import com.app.couple.Couple;
import com.app.couple.CoupleRepository;
import com.app.media.MediaDetails;
import com.app.media.MediaDetailsService;
import com.app.tracking.MediaStatus;
import com.app.tracking.MediaTrack;
import com.app.tracking.MediaTrackRepository;
import com.app.tracking.ResourceNotFoundException;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class MatchService {

	private final MatchLikeRepository matchLikeRepository;
	private final MediaTrackRepository mediaTrackRepository;
	private final CoupleRepository coupleRepository;
	private final MediaDetailsService mediaDetailsService;
	private final SimpMessagingTemplate messagingTemplate;

	public MatchService(MatchLikeRepository matchLikeRepository, MediaTrackRepository mediaTrackRepository,
			CoupleRepository coupleRepository, MediaDetailsService mediaDetailsService,
			SimpMessagingTemplate messagingTemplate) {
		this.matchLikeRepository = matchLikeRepository;
		this.mediaTrackRepository = mediaTrackRepository;
		this.coupleRepository = coupleRepository;
		this.mediaDetailsService = mediaDetailsService;
		this.messagingTemplate = messagingTemplate;
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
			createMatch(couple, request);
		}

		return new LikeResponse(matched);
	}

	private void createMatch(Couple couple, LikeRequest request) {
		MediaDetails details = mediaDetailsService.getDetails(request.mediaType(), request.tmdbId());

		MediaTrack track = new MediaTrack(couple, request.tmdbId(), request.mediaType(), MediaStatus.WANT_TO_SEE);
		track.setGenreIds(details.genreIds());
		mediaTrackRepository.save(track);

		MatchEvent event = new MatchEvent(request.tmdbId(), details.title(), request.mediaType());
		messagingTemplate.convertAndSend("/topic/couple/" + couple.getId() + "/match", event);
	}
}
