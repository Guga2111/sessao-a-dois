package com.app.match;

import com.app.couple.Couple;
import com.app.couple.CoupleRepository;
import com.app.media.MediaType;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class MatchRejectRepositoryTest {

	@Autowired
	private MatchRejectRepository matchRejectRepository;

	@Autowired
	private CoupleRepository coupleRepository;

	private Couple persistedCouple() {
		return coupleRepository.save(new Couple(UUID.randomUUID(), "REJ" + UUID.randomUUID().toString().substring(0, 4)));
	}

	@Test
	void findByCoupleIdAndUserIdAndTmdbIdFindsOwnReject() {
		Couple couple = persistedCouple();
		UUID userId = UUID.randomUUID();
		matchRejectRepository.save(new MatchReject(couple.getId(), userId, 603L, MediaType.MOVIE));

		assertThat(matchRejectRepository.findByCoupleIdAndUserIdAndTmdbId(couple.getId(), userId, 603L)).isPresent();
		assertThat(matchRejectRepository.findByCoupleIdAndUserIdAndTmdbId(couple.getId(), UUID.randomUUID(), 603L))
			.isEmpty();
		assertThat(matchRejectRepository.findByCoupleIdAndUserIdAndTmdbId(couple.getId(), userId, 999L)).isEmpty();
	}

	@Test
	void duplicateRejectFromSameUserForSameTitleViolatesUniqueConstraint() {
		Couple couple = persistedCouple();
		UUID userId = UUID.randomUUID();
		matchRejectRepository.saveAndFlush(new MatchReject(couple.getId(), userId, 603L, MediaType.MOVIE));

		assertThatThrownBy(() ->
			matchRejectRepository.saveAndFlush(new MatchReject(couple.getId(), userId, 603L, MediaType.MOVIE))
		).isInstanceOf(DataIntegrityViolationException.class);
	}
}
