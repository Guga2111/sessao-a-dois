package com.app.match;

import com.app.media.MediaType;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Escopo da porta na exclusao de conta (epico 9, US-007): apaga os {@code match_like}/
 * {@code match_reject} DO USUARIO, e nada do ex-parceiro - o recorte e o {@code user_id}, nunca o
 * {@code couple_id}.
 */
@DataJpaTest
class MatchFacadeTest {

	@Autowired
	private MatchLikeRepository matchLikeRepository;

	@Autowired
	private MatchRejectRepository matchRejectRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void deleteUserDataRemovesOnlyTheUsersLikesAndRejects() {
		MatchFacade facade = new MatchFacade(matchLikeRepository, matchRejectRepository);
		UUID coupleId = UUID.randomUUID();
		UUID leaving = UUID.randomUUID();
		UUID partner = UUID.randomUUID();

		matchLikeRepository.save(new MatchLike(coupleId, leaving, 603L, MediaType.MOVIE));
		matchLikeRepository.save(new MatchLike(coupleId, partner, 603L, MediaType.MOVIE));
		matchRejectRepository.save(new MatchReject(coupleId, leaving, 550L, MediaType.MOVIE));
		matchRejectRepository.save(new MatchReject(coupleId, partner, 550L, MediaType.MOVIE));
		entityManager.flush();

		facade.deleteUserData(leaving);
		entityManager.flush();
		entityManager.clear();

		assertThat(matchLikeRepository.findByCoupleIdAndUserIdAndTmdbId(coupleId, leaving, 603L)).isEmpty();
		assertThat(matchRejectRepository.findByCoupleIdAndUserIdAndTmdbId(coupleId, leaving, 550L)).isEmpty();
		assertThat(matchLikeRepository.findByCoupleIdAndUserIdAndTmdbId(coupleId, partner, 603L)).isPresent();
		assertThat(matchRejectRepository.findByCoupleIdAndUserIdAndTmdbId(coupleId, partner, 550L)).isPresent();
	}
}
