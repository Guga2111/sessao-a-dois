package com.app.match;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MatchLikeRepository extends JpaRepository<MatchLike, UUID> {

	Optional<MatchLike> findByCoupleIdAndUserIdAndTmdbId(UUID coupleId, UUID userId, Long tmdbId);

	Optional<MatchLike> findFirstByCoupleIdAndTmdbIdAndUserIdNot(UUID coupleId, Long tmdbId, UUID userId);

	@Query("SELECT ml FROM MatchLike ml WHERE ml.coupleId = :coupleId "
		+ "AND ml.userId != :currentUserId "
		+ "AND NOT EXISTS (SELECT 1 FROM MatchLike m WHERE m.coupleId = ml.coupleId "
		+ "AND m.userId = :currentUserId AND m.tmdbId = ml.tmdbId) "
		+ "AND NOT EXISTS (SELECT 1 FROM MatchReject r WHERE r.coupleId = ml.coupleId "
		+ "AND r.userId = :currentUserId AND r.tmdbId = ml.tmdbId) "
		+ "AND NOT EXISTS (SELECT 1 FROM MediaTrack mt WHERE mt.coupleId = ml.coupleId AND mt.tmdbId = ml.tmdbId)")
	Page<MatchLike> findPendingForUser(@Param("coupleId") UUID coupleId, @Param("currentUserId") UUID currentUserId,
			Pageable pageable);
}
