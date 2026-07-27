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

	@Query("SELECT ml FROM MatchLike ml WHERE ml.couple.id = :coupleId "
		+ "AND ml.userId != :currentUserId "
		+ "AND ml.tmdbId NOT IN (SELECT m.tmdbId FROM MatchLike m WHERE m.couple.id = :coupleId AND m.userId = :currentUserId) "
		+ "AND ml.tmdbId NOT IN (SELECT r.tmdbId FROM MatchReject r WHERE r.couple.id = :coupleId AND r.userId = :currentUserId) "
		+ "AND ml.tmdbId NOT IN (SELECT mt.tmdbId FROM MediaTrack mt WHERE mt.couple.id = :coupleId)")
	Page<MatchLike> findPendingForUser(@Param("coupleId") UUID coupleId, @Param("currentUserId") UUID currentUserId,
			Pageable pageable);
}
