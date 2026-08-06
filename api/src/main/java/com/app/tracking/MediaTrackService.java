package com.app.tracking;

import com.app.common.ResourceNotFoundException;

import com.app.couple.Couple;
import com.app.couple.CoupleRepository;
import com.app.media.MediaDetails;
import com.app.media.MediaDetailsService;
import com.app.media.MediaType;
import com.app.user.User;
import com.app.user.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MediaTrackService {

	private static final Logger log = LoggerFactory.getLogger(MediaTrackService.class);

	/** Server-side ceiling for {@code size}, regardless of what the caller requests. */
	static final int MAX_PAGE_SIZE = 50;

	private final MediaTrackRepository mediaTrackRepository;
	private final CoupleRepository coupleRepository;
	private final UserRepository userRepository;
	private final MediaDetailsService mediaDetailsService;
	private final RatingRequestService ratingRequestService;
	private final MediaTrackMapper mediaTrackMapper;

	public MediaTrackService(MediaTrackRepository mediaTrackRepository, CoupleRepository coupleRepository,
			UserRepository userRepository, MediaDetailsService mediaDetailsService,
			RatingRequestService ratingRequestService, MediaTrackMapper mediaTrackMapper) {
		this.mediaTrackRepository = mediaTrackRepository;
		this.coupleRepository = coupleRepository;
		this.userRepository = userRepository;
		this.mediaDetailsService = mediaDetailsService;
		this.ratingRequestService = ratingRequestService;
		this.mediaTrackMapper = mediaTrackMapper;
	}

	@Transactional
	public MediaTrackResponse addTrack(UUID coupleId, UUID userId, CreateMediaTrackRequest request) {
		if (request.status() != MediaStatus.WATCHED
				&& (request.rating() != null
					|| (request.opinion() != null && !request.opinion().isBlank()))) {
			throw new IllegalArgumentException("rating e opinion so podem ser enviados com status WATCHED");
		}

		Couple couple = coupleRepository.findById(coupleId)
			.orElseThrow(() -> new ResourceNotFoundException("casal nao encontrado"));
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new ResourceNotFoundException("usuario nao encontrado"));

		MediaTrack track = new MediaTrack(couple, request.tmdbId(), request.mediaType(), request.status());
		track.setWatchedDate(request.watchedDate());
		track.setRuntime(request.runtime());
		applyTmdbMetadata(track, request.mediaType(), request.tmdbId());

		UserReview review = new UserReview(track, user, request.rating(), request.opinion());
		track.getReviews().add(review);

		MediaTrack saved = mediaTrackRepository.save(track);
		if (saved.getStatus() == MediaStatus.WATCHED) {
			ratingRequestService.onRatingRegistered(saved, userId);
		}
		return mediaTrackMapper.toResponse(saved, resolveMemberNames(couple));
	}

	/** Two-column projection (no metadata, no reviews) used by MatchScreen to know which titles are already tracked. */
	public List<TrackKeyResponse> listKeys(UUID coupleId) {
		return mediaTrackRepository.findKeysByCoupleId(coupleId).stream()
			.map(key -> new TrackKeyResponse(key.getMediaType(), key.getTmdbId()))
			.toList();
	}

	/**
	 * Paginated variant used when the caller filters by status. Response shape is Spring Data's
	 * standard {@code Page} JSON: {@code content} (the page items), {@code totalElements} (total
	 * for the status), plus {@code totalPages}, {@code number}, {@code size}, etc.
	 */
	@Transactional
	public Page<MediaTrackResponse> listByStatusPaged(UUID coupleId, MediaStatus status, int page, int size) {
		int safePage = Math.max(page, 0);
		int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		Pageable pageable = PageRequest.of(safePage, safeSize);

		// Paginate over ids only (no collection fetch), then fetch that page's rows with
		// reviews/couple eagerly loaded by id - combining a collection fetch join with
		// Pageable would make Hibernate paginate in memory instead of at the DB (HHH000104).
		Page<UUID> idPage = mediaTrackRepository.findIdsByCoupleIdAndStatusOrderByCreatedAtDesc(coupleId, status,
				pageable);
		List<MediaTrack> tracks = mediaTrackRepository.findByIdIn(idPage.getContent());
		Map<UUID, MediaTrack> tracksById = tracks.stream().collect(Collectors.toMap(MediaTrack::getId, t -> t));
		List<MediaTrack> orderedTracks = idPage.getContent().stream().map(tracksById::get).toList();

		orderedTracks.forEach(this::healMetadata);

		Map<UUID, String> userNames = resolveMemberNames(orderedTracks);
		List<MediaTrackResponse> content = orderedTracks.stream()
			.map(track -> mediaTrackMapper.toResponse(track, userNames))
			.toList();
		return new PageImpl<>(content, pageable, idPage.getTotalElements());
	}

	/**
	 * Self-heals a pre-V4 row (title null) by fetching TMDB once and persisting the result, so
	 * every read after this one skips TMDB entirely for this row. A TMDB failure leaves the
	 * fields null instead of failing the request - the row is retried on the next read.
	 */
	private void healMetadata(MediaTrack track) {
		if (track.getTitle() != null) {
			return;
		}
		try {
			MediaDetails details = mediaDetailsService.getDetails(track.getMediaType(), track.getTmdbId());
			track.setTitle(details.title());
			track.setPosterUrl(details.posterUrl());
			track.setReleaseYear(details.year());
			mediaTrackRepository.save(track);
		}
		catch (RuntimeException ex) {
			log.warn("Nao foi possivel auto-curar os metadados do titulo {} no TMDB: {}", track.getTmdbId(),
					ex.getMessage());
		}
	}

	@Transactional
	public MediaTrackResponse markAsWatched(UUID trackId, UUID coupleId, UUID userId, WatchRequest request) {
		MediaTrack track = findOwnedTrack(trackId, coupleId);
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new ResourceNotFoundException("usuario nao encontrado"));

		track.setStatus(MediaStatus.WATCHED);
		track.setWatchedDate(LocalDate.now());

		track.getReviews().stream()
			.filter(r -> r.getUser().getId().equals(userId))
			.findFirst()
			.ifPresentOrElse(
				r -> {
					r.setRating(request.rating());
					r.setOpinion(request.opinion());
				},
				() -> track.getReviews().add(new UserReview(track, user, request.rating(), request.opinion()))
			);

		MediaTrack saved = mediaTrackRepository.save(track);
		ratingRequestService.onRatingRegistered(saved, userId);
		return mediaTrackMapper.toResponse(saved, resolveMemberNames(saved.getCouple()));
	}

	/**
	 * Moves a track from WANT_TO_SEE to WATCHING without touching reviews or watchedDate.
	 * Any other requested status, or a track not currently in WANT_TO_SEE, is a 400.
	 */
	@Transactional
	public MediaTrackResponse startWatching(UUID trackId, UUID coupleId, MediaStatus requestedStatus) {
		if (requestedStatus != MediaStatus.WATCHING) {
			throw new IllegalArgumentException("transicao de status invalida");
		}

		MediaTrack track = findOwnedTrack(trackId, coupleId);
		if (track.getStatus() != MediaStatus.WANT_TO_SEE) {
			throw new IllegalArgumentException("transicao de status invalida");
		}

		track.setStatus(MediaStatus.WATCHING);
		MediaTrack saved = mediaTrackRepository.save(track);
		return mediaTrackMapper.toResponse(saved, resolveMemberNames(saved.getCouple()));
	}

	@Transactional
	public void deleteTrack(UUID trackId, UUID coupleId) {
		MediaTrack track = findOwnedTrack(trackId, coupleId);
		ratingRequestService.onTrackDeleted(track);
		mediaTrackRepository.delete(track);
	}

	/**
	 * Populates genres, title, poster and release year from the same TMDB response - a flaky
	 * TMDB must not fail track creation, so a failure here just leaves those fields empty/null.
	 */
	private void applyTmdbMetadata(MediaTrack track, MediaType mediaType, Long tmdbId) {
		try {
			MediaDetails details = mediaDetailsService.getDetails(mediaType, tmdbId);
			track.setGenreIds(details.genreIds() == null ? List.of() : details.genreIds());
			track.setTitle(details.title());
			track.setPosterUrl(details.posterUrl());
			track.setReleaseYear(details.year());
		}
		catch (RuntimeException ex) {
			log.warn("Nao foi possivel obter os metadados do titulo {} no TMDB: {}", tmdbId, ex.getMessage());
			track.setGenreIds(List.of());
		}
	}

	private MediaTrack findOwnedTrack(UUID trackId, UUID coupleId) {
		MediaTrack track = mediaTrackRepository.findById(trackId)
			.orElseThrow(() -> new ResourceNotFoundException("titulo nao encontrado"));

		if (!track.getCouple().getId().equals(coupleId)) {
			throw new ResourceNotFoundException("titulo nao encontrado");
		}

		return track;
	}

	/** Resolves every distinct couple member name in {@code tracks} with a single query. */
	private Map<UUID, String> resolveMemberNames(List<MediaTrack> tracks) {
		List<UUID> memberIds = tracks.stream()
			.flatMap(track -> MediaTrackMapper.memberIds(track.getCouple()).stream())
			.distinct()
			.toList();
		return resolveMemberNames(memberIds);
	}

	private Map<UUID, String> resolveMemberNames(Couple couple) {
		return resolveMemberNames(MediaTrackMapper.memberIds(couple));
	}

	private Map<UUID, String> resolveMemberNames(Collection<UUID> memberIds) {
		Map<UUID, String> names = new HashMap<>();
		for (User user : userRepository.findAllById(memberIds)) {
			names.put(user.getId(), user.getName());
		}
		return names;
	}
}
