package com.app.tracking;

import com.app.media.MediaDetails;
import com.app.media.MediaType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Single port other features go through to create/inspect a {@link MediaTrack} - keeps
 * MediaTrack/MediaTrackRepository/MediaStatus internal to com.app.tracking.
 */
@Service
public class TrackingFacade {

	private final MediaTrackRepository mediaTrackRepository;
	private final UserReviewRepository userReviewRepository;

	public TrackingFacade(MediaTrackRepository mediaTrackRepository, UserReviewRepository userReviewRepository) {
		this.mediaTrackRepository = mediaTrackRepository;
		this.userReviewRepository = userReviewRepository;
	}

	public boolean isTracked(UUID coupleId, Long tmdbId) {
		return mediaTrackRepository.existsByCoupleIdAndTmdbId(coupleId, tmdbId);
	}

	/**
	 * Creates the track originated from a mutual match - always starts as WANT_TO_SEE, populated
	 * from the same TMDB response the caller already fetched (no extra TMDB call here).
	 */
	@Transactional
	public void createTrackFromMatch(UUID coupleId, Long tmdbId, MediaType mediaType, MediaDetails details) {
		MediaTrack track = new MediaTrack(coupleId, tmdbId, mediaType, MediaStatus.WANT_TO_SEE);
		track.setGenreIds(details.genreIds());
		track.setTitle(details.title());
		track.setPosterUrl(details.posterUrl());
		track.setReleaseYear(details.year());
		mediaTrackRepository.save(track);
	}

	/**
	 * Exclusao de conta (epico 9, US-007): apaga o que pertence a ESTA feature e e do usuario -
	 * apenas os {@code user_review} dele. <b>Nao toca em {@code media_track}</b>: o titulo
	 * rastreado pertence ao casal ({@code couple_id}), nao ao usuario, e continua sendo o
	 * historico do ex-parceiro (D13).
	 */
	@Transactional
	public void deleteUserData(UUID userId) {
		userReviewRepository.deleteByUserId(userId);
	}
}
