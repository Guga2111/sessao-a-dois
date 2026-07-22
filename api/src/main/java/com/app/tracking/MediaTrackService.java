package com.app.tracking;

import com.app.couple.Couple;
import com.app.couple.CoupleRepository;
import com.app.user.User;
import com.app.user.UserRepository;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MediaTrackService {

	private final MediaTrackRepository mediaTrackRepository;
	private final CoupleRepository coupleRepository;
	private final UserRepository userRepository;

	public MediaTrackService(MediaTrackRepository mediaTrackRepository, CoupleRepository coupleRepository,
			UserRepository userRepository) {
		this.mediaTrackRepository = mediaTrackRepository;
		this.coupleRepository = coupleRepository;
		this.userRepository = userRepository;
	}

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

		UserReview review = new UserReview(track, user, request.rating(), request.opinion());
		track.getReviews().add(review);

		MediaTrack saved = mediaTrackRepository.save(track);
		return toResponse(saved);
	}

	public List<MediaTrackResponse> listByStatus(UUID coupleId, MediaStatus status) {
		List<MediaTrack> tracks = status == null
			? mediaTrackRepository.findByCoupleId(coupleId)
			: mediaTrackRepository.findByCoupleIdAndStatus(coupleId, status);

		return tracks.stream().map(this::toResponse).toList();
	}

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

		return toResponse(mediaTrackRepository.save(track));
	}

	public void deleteTrack(UUID trackId, UUID coupleId) {
		MediaTrack track = findOwnedTrack(trackId, coupleId);
		mediaTrackRepository.delete(track);
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
