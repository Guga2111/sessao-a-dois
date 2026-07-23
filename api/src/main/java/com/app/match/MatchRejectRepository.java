package com.app.match;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MatchRejectRepository extends JpaRepository<MatchReject, UUID> {

	Optional<MatchReject> findByCoupleIdAndUserIdAndTmdbId(UUID coupleId, UUID userId, Long tmdbId);
}
