package com.app.tracking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserReviewRepository extends JpaRepository<UserReview, UUID> {

	Optional<UserReview> findByMediaTrackIdAndUserId(UUID trackId, UUID userId);

	@Query("SELECT AVG(ur.rating) FROM UserReview ur "
		+ "WHERE ur.mediaTrack.couple.id = :coupleId AND ur.mediaTrack.status = :status")
	Double findAverageRatingByCoupleIdAndMediaTrackStatus(@Param("coupleId") UUID coupleId,
			@Param("status") MediaStatus status);
}
