package com.app.tracking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserReviewRepository extends JpaRepository<UserReview, UUID> {

	Optional<UserReview> findByMediaTrackIdAndUserId(UUID trackId, UUID userId);

	@Query("SELECT AVG(ur.rating) FROM UserReview ur "
		+ "WHERE ur.mediaTrack.coupleId = :coupleId AND ur.mediaTrack.status = :status")
	Double findAverageRatingByCoupleIdAndMediaTrackStatus(@Param("coupleId") UUID coupleId,
			@Param("status") MediaStatus status);

	/**
	 * Exclusao de conta (epico 9, US-007): apaga so as avaliacoes DO USUARIO, num unico statement.
	 * O {@code media_track} do casal continua no banco - ele pertence ao {@code couple_id}, nao ao
	 * usuario (D13), e e o historico que fica com o ex-parceiro.
	 */
	@Modifying
	@Query("DELETE FROM UserReview ur WHERE ur.user.id = :userId")
	int deleteByUserId(@Param("userId") UUID userId);
}
