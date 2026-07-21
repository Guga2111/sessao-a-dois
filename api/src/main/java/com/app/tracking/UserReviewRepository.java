package com.app.tracking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserReviewRepository extends JpaRepository<UserReview, UUID> {

	Optional<UserReview> findByMediaTrackIdAndUserId(UUID trackId, UUID userId);
}
