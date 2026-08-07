package com.app.couple;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CoupleRepository extends JpaRepository<Couple, UUID> {

	/**
	 * Resolve o casal do CONVITE (epico 9, US-001) ignorando casais dissolvidos.
	 *
	 * Substitui o antigo {@code findByInviteCode}, que foi removido de proposito e nao deve voltar: e por
	 * ele que um usuario ENTRA num casal, e esse caminho nao parte do userId, entao nenhum filtro de
	 * "casal ativo" o alcanca por tabela. Cobre tambem linhas legadas dissolvidas com o codigo ainda
	 * preenchido a mao, que {@code Couple.dissolve()} sozinho nao consertaria.
	 */
	@Query("select c from Couple c where c.inviteCode = :inviteCode and c.dissolvedAt is null")
	Optional<Couple> findActiveByInviteCode(@Param("inviteCode") String inviteCode);

	/**
	 * Resolve o casal DO USUARIO (epico 9, US-001) ignorando casais dissolvidos.
	 *
	 * A partir da dissolucao um usuario pode ter varias linhas em {@code couples} (uma ativa e N
	 * dissolvidas); sem o filtro, este {@code Optional} viraria
	 * {@code IncorrectResultSizeDataAccessException} no primeiro usuario que dissolvesse e formasse um
	 * casal novo.
	 */
	@Query("select c from Couple c where (c.user1Id = :userId or c.user2Id = :userId) and c.dissolvedAt is null")
	Optional<Couple> findActiveByUserId(@Param("userId") UUID userId);

	boolean existsByInviteCode(String inviteCode);
}
