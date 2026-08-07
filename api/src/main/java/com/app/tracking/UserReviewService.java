package com.app.tracking;

import com.app.common.ResourceNotFoundException;

import com.app.couple.CoupleFacade;
import com.app.user.User;
import com.app.user.UserRepository;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UserReviewService {

	private final UserReviewRepository userReviewRepository;
	private final MediaTrackRepository mediaTrackRepository;
	private final UserRepository userRepository;
	private final CoupleFacade coupleFacade;
	private final MediaTrackMapper mediaTrackMapper;
	private final RatingRequestService ratingRequestService;

	public UserReviewService(UserReviewRepository userReviewRepository, MediaTrackRepository mediaTrackRepository,
			UserRepository userRepository, CoupleFacade coupleFacade, MediaTrackMapper mediaTrackMapper,
			RatingRequestService ratingRequestService) {
		this.userReviewRepository = userReviewRepository;
		this.mediaTrackRepository = mediaTrackRepository;
		this.userRepository = userRepository;
		this.coupleFacade = coupleFacade;
		this.mediaTrackMapper = mediaTrackMapper;
		this.ratingRequestService = ratingRequestService;
	}

	@Transactional
	public MediaTrackResponse upsertReview(UUID trackId, UUID userId, UUID coupleId, UpsertReviewRequest request) {
		MediaTrack track = mediaTrackRepository.findById(trackId)
			.orElseThrow(() -> new ResourceNotFoundException("titulo nao encontrado"));

		if (!track.getCoupleId().equals(coupleId)) {
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

		List<UUID> memberIds = coupleFacade.memberIds(track.getCoupleId());
		return mediaTrackMapper.toResponse(track, memberIds, resolveMemberNames(memberIds));
	}

	private Map<UUID, String> resolveMemberNames(List<UUID> memberIds) {
		Map<UUID, String> names = new HashMap<>();
		for (User user : userRepository.findAllById(memberIds)) {
			names.put(user.getId(), user.getName());
		}
		return names;
	}
}
