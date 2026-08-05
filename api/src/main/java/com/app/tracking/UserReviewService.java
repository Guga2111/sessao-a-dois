package com.app.tracking;

import com.app.common.ResourceNotFoundException;

import com.app.user.User;
import com.app.user.UserRepository;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class UserReviewService {

	private final UserReviewRepository userReviewRepository;
	private final MediaTrackRepository mediaTrackRepository;
	private final UserRepository userRepository;
	private final MediaTrackMapper mediaTrackMapper;
	private final RatingRequestService ratingRequestService;

	public UserReviewService(UserReviewRepository userReviewRepository, MediaTrackRepository mediaTrackRepository,
			UserRepository userRepository, MediaTrackMapper mediaTrackMapper,
			RatingRequestService ratingRequestService) {
		this.userReviewRepository = userReviewRepository;
		this.mediaTrackRepository = mediaTrackRepository;
		this.userRepository = userRepository;
		this.mediaTrackMapper = mediaTrackMapper;
		this.ratingRequestService = ratingRequestService;
	}

	@Transactional
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

		ratingRequestService.onRatingRegistered(track, userId);

		return mediaTrackMapper.toResponse(track, resolveMemberNames(track));
	}

	private Map<UUID, String> resolveMemberNames(MediaTrack track) {
		Map<UUID, String> names = new HashMap<>();
		for (User user : userRepository.findAllById(MediaTrackMapper.memberIds(track.getCouple()))) {
			names.put(user.getId(), user.getName());
		}
		return names;
	}
}
