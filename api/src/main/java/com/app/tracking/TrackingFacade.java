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

	public TrackingFacade(MediaTrackRepository mediaTrackRepository) {
		this.mediaTrackRepository = mediaTrackRepository;
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
}
