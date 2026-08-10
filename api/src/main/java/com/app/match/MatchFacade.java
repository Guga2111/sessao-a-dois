package com.app.match;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Porta de {@code com.app.match} para as demais features (epico 9, US-007).
 *
 * Nasce com um unico metodo, e isso e deliberado: ela existe por causa da regra de dependencia entre
 * features (uma feature nao importa o repositorio/entidade de outra), nao porque {@code match}
 * precisasse de uma fachada rica. Quem apaga dado de {@code match_like}/{@code match_reject} de fora
 * desta feature passa por aqui - nunca pelos repositorios.
 */
@Component
public class MatchFacade {

	private final MatchLikeRepository matchLikeRepository;
	private final MatchRejectRepository matchRejectRepository;

	public MatchFacade(MatchLikeRepository matchLikeRepository, MatchRejectRepository matchRejectRepository) {
		this.matchLikeRepository = matchLikeRepository;
		this.matchRejectRepository = matchRejectRepository;
	}

	/**
	 * Exclusao de conta: apaga os likes e rejects DO USUARIO. As linhas do ex-parceiro no mesmo
	 * casal permanecem - o escopo e o usuario, nunca o {@code couple_id}.
	 */
	@Transactional
	public void deleteUserData(UUID userId) {
		matchLikeRepository.deleteByUserId(userId);
		matchRejectRepository.deleteByUserId(userId);
	}
}
