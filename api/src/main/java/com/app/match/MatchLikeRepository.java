package com.app.match;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MatchLikeRepository extends JpaRepository<MatchLike, UUID> {

	Optional<MatchLike> findByCoupleIdAndUserIdAndTmdbId(UUID coupleId, UUID userId, Long tmdbId);

	Optional<MatchLike> findFirstByCoupleIdAndTmdbIdAndUserIdNot(UUID coupleId, Long tmdbId, UUID userId);
}
