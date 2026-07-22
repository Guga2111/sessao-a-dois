package com.app.match;

import com.app.couple.Couple;
import com.app.couple.CoupleRepository;
import com.app.tracking.MediaTrackRepository;
import com.app.tracking.ResourceNotFoundException;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class MatchService {

	private final MatchLikeRepository matchLikeRepository;
	private final MediaTrackRepository mediaTrackRepository;
	private final CoupleRepository coupleRepository;

	public MatchService(MatchLikeRepository matchLikeRepository, MediaTrackRepository mediaTrackRepository,
			CoupleRepository coupleRepository) {
		this.matchLikeRepository = matchLikeRepository;
		this.mediaTrackRepository = mediaTrackRepository;
		this.coupleRepository = coupleRepository;
	}

	public LikeResponse like(UUID coupleId, UUID userId, LikeRequest request) {
		if (mediaTrackRepository.existsByCoupleIdAndTmdbId(coupleId, request.tmdbId())) {
			throw new TitleAlreadyTrackedException();
		}

		if (matchLikeRepository.findByCoupleIdAndUserIdAndTmdbId(coupleId, userId, request.tmdbId()).isEmpty()) {
			Couple couple = coupleRepository.findById(coupleId)
				.orElseThrow(() -> new ResourceNotFoundException("casal nao encontrado"));

			MatchLike like = new MatchLike(couple, userId, request.tmdbId(), request.mediaType());
			matchLikeRepository.save(like);
		}

		boolean matched = matchLikeRepository
			.findFirstByCoupleIdAndTmdbIdAndUserIdNot(coupleId, request.tmdbId(), userId)
			.isPresent();

		return new LikeResponse(matched);
	}
}
