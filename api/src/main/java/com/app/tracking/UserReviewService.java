package com.app.tracking;

import com.app.user.User;
import com.app.user.UserRepository;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserReviewService {

	private final UserReviewRepository userReviewRepository;
	private final MediaTrackRepository mediaTrackRepository;
	private final UserRepository userRepository;
	private final MediaTrackService mediaTrackService;

	public UserReviewService(UserReviewRepository userReviewRepository, MediaTrackRepository mediaTrackRepository,
			UserRepository userRepository, MediaTrackService mediaTrackService) {
		this.userReviewRepository = userReviewRepository;
		this.mediaTrackRepository = mediaTrackRepository;
		this.userRepository = userRepository;
		this.mediaTrackService = mediaTrackService;
	}

	public MediaTrackResponse upsertReview(UUID trackId, UUID userId, UUID coupleId, UpsertReviewRequest request) {
		MediaTrack track = mediaTrackRepository.findById(trackId)
			.orElseThrow(() -> new ResourceNotFoundException("titulo nao encontrado"));

		if (!track.getCouple().getId().equals(coupleId)) {
			throw new AccessDeniedException("titulo nao pertence ao casal do usuario");
		}

		userReviewRepository.findByMediaTrackIdAndUserId(trackId, userId)
			.ifPresentOrElse(
				existing -> {
					existing.setRating(request.rating());
					existing.setOpinion(request.opinion());
					userReviewRepository.save(existing);
				},
				() -> {
					User user = userRepository.findById(userId)
						.orElseThrow(() -> new ResourceNotFoundException("usuario nao encontrado"));
					UserReview review = new UserReview(track, user, request.rating(), request.opinion());
					userReviewRepository.save(review);
					track.getReviews().add(review);
				});

		return mediaTrackService.toResponse(track);
	}
}
