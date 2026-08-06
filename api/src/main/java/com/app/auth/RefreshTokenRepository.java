package com.app.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	/**
	 * Revoga, numa unica instrucao, todos os refresh tokens ativos (nao revogados
	 * e nao expirados) de um usuario - usado para derrubar a familia inteira na
	 * deteccao de reuso, sem carregar entidades em memoria (mesmo raciocinio de
	 * NotificationRepository).
	 */
	@Modifying
	@Query("UPDATE RefreshToken r SET r.revokedAt = :now WHERE r.userId = :userId AND r.revokedAt IS NULL AND r.expiresAt > :now")
	int revokeAllActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}
