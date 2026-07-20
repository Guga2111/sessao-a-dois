package com.app.couple;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

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

		var found = coupleRepository.findByInviteCode("ABC234");

		assertThat(found).isPresent();
		assertThat(found.get().getUser1Id()).isEqualTo(user1Id);
		assertThat(found.get().getUser2Id()).isNull();
		assertThat(found.get().getCreatedAt()).isNotNull();
	}

	@Test
	void findsCoupleByEitherUserId() {
		UUID user1Id = UUID.randomUUID();
		UUID user2Id = UUID.randomUUID();
		Couple couple = new Couple(user1Id, "XYZ234");
		couple.setUser2Id(user2Id);
		coupleRepository.save(couple);

		assertThat(coupleRepository.findByUser1IdOrUser2Id(user1Id, user1Id)).isPresent();
		assertThat(coupleRepository.findByUser1IdOrUser2Id(user2Id, user2Id)).isPresent();
		assertThat(coupleRepository.findByUser1IdOrUser2Id(UUID.randomUUID(), UUID.randomUUID())).isEmpty();
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
