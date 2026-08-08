package com.app.tracking;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Translates {@link MediaTrack} to {@link MediaTrackResponse}. Receives both the couple's member
 * ids ({@code CoupleFacade.memberIds}) and their names already resolved by the caller - one
 * resolution per request, not one per track - so mapping N tracks never costs N lookups.
 */
@Component
public class MediaTrackMapper {

	public MediaTrackResponse toResponse(MediaTrack track, List<UUID> memberIds, Map<UUID, String> userNames) {
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
			reviews,
			track.getTitle(),
			track.getPosterUrl(),
			track.getReleaseYear()
		);
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
