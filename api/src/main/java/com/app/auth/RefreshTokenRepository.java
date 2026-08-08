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

	/**
	 * Exclusao de conta (epico 9, US-007), passo 1 de 2: quebra a cadeia de rotacao antes do
	 * {@code DELETE}. {@code refresh_token} tem a auto-FK {@code fk_refresh_token_replaced_by}, e
	 * anular {@code replaced_by_id} num unico {@code UPDATE} e o que torna o delete seguinte
	 * independente da ordem em que as linhas saem.
	 *
	 * <p>Nao basta confiar no "o Postgres so checa a FK no fim do statement": isso vale para o
	 * Postgres, mas nao e portavel - o H2 em {@code MODE=PostgreSQL}, que e o banco de todo o
	 * ambiente de teste local, checa linha a linha, e sem este passo a cadeia de tres tokens do
	 * {@code UserDeletionIntegrityTest} estoura. Quebrar a cadeia explicitamente vale nos dois.
	 */
	@Modifying
	@Query("UPDATE RefreshToken r SET r.replacedById = null WHERE r.userId = :userId")
	int clearReplacedByForUser(@Param("userId") UUID userId);

	/**
	 * Exclusao de conta (epico 9, US-007), passo 2 de 2: apaga as linhas num <b>statement unico</b>
	 * baseado em conjunto - nunca {@code deleteAll(entidades)}, que emitiria um {@code DELETE} por
	 * linha e devolveria o problema de ordem que o passo 1 acabou de remover.
	 */
	@Modifying
	@Query("DELETE FROM RefreshToken r WHERE r.userId = :userId")
	int deleteAllByUserId(@Param("userId") UUID userId);
}
