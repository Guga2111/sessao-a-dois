package com.app.couple;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class CoupleRepositoryTest {

	@Autowired
	private CoupleRepository coupleRepository;

	@Test
	void savesAndFindsCoupleByInviteCode() {
		UUID user1Id = UUID.randomUUID();
		coupleRepository.save(new Couple(user1Id, "ABC234"));

		var found = coupleRepository.findActiveByInviteCode("ABC234");

		assertThat(found).isPresent();
		assertThat(found.get().getUser1Id()).isEqualTo(user1Id);
		assertThat(found.get().getUser2Id()).isNull();
		assertThat(found.get().getCreatedAt()).isNotNull();
		assertThat(found.get().getDissolvedAt()).isNull();
	}

	@Test
	void findActiveByUserIdIgnoresDissolvedCouple() {
		UUID user1Id = UUID.randomUUID();
		UUID user2Id = UUID.randomUUID();
		Couple couple = new Couple(user1Id, "DISS234");
		couple.setUser2Id(user2Id);
		couple.dissolve();
		coupleRepository.saveAndFlush(couple);

		assertThat(coupleRepository.findActiveByUserId(user1Id)).isEmpty();
		assertThat(coupleRepository.findActiveByUserId(user2Id)).isEmpty();
	}

	@Test
	void findActiveByUserIdReturnsOnlyTheActiveCoupleWhenTheUserAlsoHasADissolvedOne() {
		UUID userId = UUID.randomUUID();
		Couple dissolved = new Couple(userId, "OLD1234");
		dissolved.dissolve();
		coupleRepository.saveAndFlush(dissolved);
		Couple active = coupleRepository.saveAndFlush(new Couple(userId, "NEW1234"));

		var found = coupleRepository.findActiveByUserId(userId);

		assertThat(found).isPresent();
		assertThat(found.get().getId()).isEqualTo(active.getId());
	}

	/**
	 * Linha legada: casal dissolvido cujo invite_code continua preenchido (dissolvido antes da US-001,
	 * ou por um caminho futuro que esqueca o clearInviteCode). O filtro da consulta e o cinto que segura
	 * o que o dissolve() sozinho nao seguraria.
	 */
	@Test
	void findActiveByInviteCodeIgnoresDissolvedCoupleThatStillHasACode() {
		Couple legacy = new Couple(UUID.randomUUID(), "LEGACY7");
		ReflectionTestUtils.setField(legacy, "dissolvedAt", Instant.now());
		coupleRepository.saveAndFlush(legacy);

		assertThat(legacy.getInviteCode()).isEqualTo("LEGACY7");
		assertThat(coupleRepository.findActiveByInviteCode("LEGACY7")).isEmpty();
	}

	@Test
	void findsCoupleByEitherUserId() {
		UUID user1Id = UUID.randomUUID();
		UUID user2Id = UUID.randomUUID();
		Couple couple = new Couple(user1Id, "XYZ234");
		couple.setUser2Id(user2Id);
		coupleRepository.save(couple);

		assertThat(coupleRepository.findActiveByUserId(user1Id)).isPresent();
		assertThat(coupleRepository.findActiveByUserId(user2Id)).isPresent();
		assertThat(coupleRepository.findActiveByUserId(UUID.randomUUID())).isEmpty();
	}

	@Test
	void inviteCodeMustBeUnique() {
		coupleRepository.saveAndFlush(new Couple(UUID.randomUUID(), "DUPE234"));

		assertThatThrownBy(() ->
			coupleRepository.saveAndFlush(new Couple(UUID.randomUUID(), "DUPE234"))
		).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void existsByInviteCodeReflectsPersistedCouples() {
		coupleRepository.save(new Couple(UUID.randomUUID(), "EXIST12"));

		assertThat(coupleRepository.existsByInviteCode("EXIST12")).isTrue();
		assertThat(coupleRepository.existsByInviteCode("NOPE999")).isFalse();
	}
}
