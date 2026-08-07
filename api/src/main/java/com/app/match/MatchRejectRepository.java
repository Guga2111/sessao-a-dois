package com.app.match;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MatchRejectRepository extends JpaRepository<MatchReject, UUID> {

	Optional<MatchReject> findByCoupleIdAndUserIdAndTmdbId(UUID coupleId, UUID userId, Long tmdbId);

	/**
	 * Exclusao de conta (epico 9, US-007): apaga os rejects DO USUARIO num unico statement.
	 */
	@Modifying
	@Query("DELETE FROM MatchReject mr WHERE mr.userId = :userId")
	int deleteByUserId(@Param("userId") UUID userId);
}
