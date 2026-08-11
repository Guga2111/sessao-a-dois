package com.app.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

	Optional<PasswordResetToken> findByTokenHash(String tokenHash);

	/**
	 * Emitir um token novo invalida todos os anteriores do mesmo usuario - marca
	 * como usados, num unico statement baseado em conjunto, os que ainda nao
	 * foram usados nem expiraram (mesmo raciocinio de
	 * RefreshTokenRepository.revokeAllActiveByUserId).
	 */
	@Modifying(clearAutomatically = true)
	@Query("UPDATE PasswordResetToken t SET t.usedAt = :now WHERE t.userId = :userId AND t.usedAt IS NULL AND t.expiresAt > :now")
	int invalidateAllActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

	/**
	 * Limpeza agendada (US-009): apaga tokens expirados ou ja usados, num unico
	 * DELETE independentemente do volume - nunca deleteAll(entidades).
	 */
	@Modifying
	@Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :now OR t.usedAt IS NOT NULL")
	int deleteExpiredOrUsed(@Param("now") Instant now);
}
