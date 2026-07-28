package com.app.tracking;

import com.app.couple.Couple;
import com.app.couple.CoupleRepository;
import com.app.media.MediaDetailsService;
import com.app.media.MediaType;
import com.app.user.User;
import com.app.user.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

	public MediaTrackService(MediaTrackRepository mediaTrackRepository, CoupleRepository coupleRepository,
			UserRepository userRepository, MediaDetailsService mediaDetailsService,
			RatingRequestService ratingRequestService) {
		this.mediaTrackRepository = mediaTrackRepository;
		this.coupleRepository = coupleRepository;
		this.userRepository = userRepository;
		this.mediaDetailsService = mediaDetailsService;
		this.ratingRequestService = ratingRequestService;
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
		track.setGenreIds(fetchGenreIds(request.mediaType(), request.tmdbId()));

		UserReview review = new UserReview(track, user, request.rating(), request.opinion());
		track.getReviews().add(review);

		MediaTrack saved = mediaTrackRepository.save(track);
		if (saved.getStatus() == MediaStatus.WATCHED) {
			ratingRequestService.onRatingRegistered(saved, userId);
		}
		return toResponse(saved);
	}

	public List<MediaTrackResponse> listByStatus(UUID coupleId, MediaStatus status) {
		List<MediaTrack> tracks = status == null
			? mediaTrackRepository.findByCoupleId(coupleId)
			: mediaTrackRepository.findByCoupleIdAndStatus(coupleId, status);

		return tracks.stream().map(this::toResponse).toList();
	}

	/**
	 * Paginated variant used when the caller filters by status. Response shape is Spring Data's
	 * standard {@code Page} JSON: {@code content} (the page items), {@code totalElements} (total
	 * for the status), plus {@code totalPages}, {@code number}, {@code size}, etc.
	 */
	public Page<MediaTrackResponse> listByStatusPaged(UUID coupleId, MediaStatus status, int page, int size) {
		int safePage = Math.max(page, 0);
		int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		Pageable pageable = PageRequest.of(safePage, safeSize);

		return mediaTrackRepository.findByCoupleIdAndStatusOrderByCreatedAtDesc(coupleId, status, pageable)
			.map(this::toResponse);
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
		return toResponse(saved);
	}

	/**
	 * Moves a track from WANT_TO_SEE to WATCHING without touching reviews or watchedDate.
	 * Any other requested status, or a track not currently in WANT_TO_SEE, is a 400.
	 */
	public MediaTrackResponse startWatching(UUID trackId, UUID coupleId, MediaStatus requestedStatus) {
		if (requestedStatus != MediaStatus.WATCHING) {
			throw new IllegalArgumentException("transicao de status invalida");
		}

		MediaTrack track = findOwnedTrack(trackId, coupleId);
		if (track.getStatus() != MediaStatus.WANT_TO_SEE) {
			throw new IllegalArgumentException("transicao de status invalida");
		}

		track.setStatus(MediaStatus.WATCHING);
		return toResponse(mediaTrackRepository.save(track));
	}

	@Transactional
	public void deleteTrack(UUID trackId, UUID coupleId) {
		MediaTrack track = findOwnedTrack(trackId, coupleId);
		ratingRequestService.onTrackDeleted(track);
		mediaTrackRepository.delete(track);
	}

	private List<Integer> fetchGenreIds(MediaType mediaType, Long tmdbId) {
		try {
			List<Integer> genreIds = mediaDetailsService.getDetails(mediaType, tmdbId).genreIds();
			return genreIds == null ? List.of() : genreIds;
		}
		catch (RuntimeException ex) {
			log.warn("Nao foi possivel obter os generos do titulo {} no TMDB: {}", tmdbId, ex.getMessage());
			return List.of();
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

	MediaTrackResponse toResponse(MediaTrack track) {
		Couple couple = track.getCouple();
		List<UUID> memberIds = new ArrayList<>();
		memberIds.add(couple.getUser1Id());
		if (couple.getUser2Id() != null) {
			memberIds.add(couple.getUser2Id());
		}

		List<ReviewDto> reviews = memberIds.stream()
			.map(memberId -> toReviewDto(memberId, track))
			.toList();

		return new MediaTrackResponse(
			track.getId(),
			track.getTmdbId(),
			track.getMediaType(),
			track.getStatus(),
			track.getWatchedDate(),
			track.getRuntime(),
			track.getCreatedAt(),
			reviews
		);
	}

	private ReviewDto toReviewDto(UUID memberId, MediaTrack track) {
		Optional<UserReview> existingReview = track.getReviews().stream()
			.filter(review -> review.getUser().getId().equals(memberId))
			.findFirst();

		String userName = userRepository.findById(memberId).map(User::getName).orElse(null);
		Integer rating = existingReview.map(UserReview::getRating).orElse(null);
		String opinion = existingReview.map(UserReview::getOpinion).orElse(null);

		return new ReviewDto(memberId, userName, rating, opinion);
	}
}
