package com.app.tracking;

import com.app.couple.Couple;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Translates {@link MediaTrack} to {@link MediaTrackResponse}. Receives member names already
 * resolved (one {@code userRepository.findAllById} call per request, done by the caller) instead
 * of injecting {@code UserRepository} itself, so mapping N tracks never costs N user lookups.
 */
@Component
public class MediaTrackMapper {

	public MediaTrackResponse toResponse(MediaTrack track, Map<UUID, String> userNames) {
		List<UUID> memberIds = memberIds(track.getCouple());

		List<ReviewDto> reviews = memberIds.stream()
			.map(memberId -> toReviewDto(memberId, track, userNames))
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

	/** The couple's member ids (skipping a null {@code user2Id} for a couple with no partner yet). */
	public static List<UUID> memberIds(Couple couple) {
		List<UUID> memberIds = new ArrayList<>();
		memberIds.add(couple.getUser1Id());
		if (couple.getUser2Id() != null) {
			memberIds.add(couple.getUser2Id());
		}
		return memberIds;
	}

	private ReviewDto toReviewDto(UUID memberId, MediaTrack track, Map<UUID, String> userNames) {
		Optional<UserReview> existingReview = track.getReviews().stream()
			.filter(review -> review.getUser().getId().equals(memberId))
			.findFirst();

		String userName = userNames.get(memberId);
		Integer rating = existingReview.map(UserReview::getRating).orElse(null);
		String opinion = existingReview.map(UserReview::getOpinion).orElse(null);

		return new ReviewDto(memberId, userName, rating, opinion);
	}
}
