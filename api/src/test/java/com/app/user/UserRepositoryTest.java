package com.app.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class UserRepositoryTest {

	@Autowired
	private UserRepository userRepository;

	@Test
	void savesAndFindsUserByEmail() {
		userRepository.save(new User("Ana", "ana@example.com", "hashed-password"));

		var found = userRepository.findByEmail("ana@example.com");

		assertThat(found).isPresent();
		assertThat(found.get().getName()).isEqualTo("Ana");
		assertThat(found.get().getId()).isNotNull();
		assertThat(found.get().getCreatedAt()).isNotNull();
	}

	@Test
	void existsByEmailReflectsPersistedUsers() {
		userRepository.save(new User("Bruno", "bruno@example.com", "hashed-password"));

		assertThat(userRepository.existsByEmail("bruno@example.com")).isTrue();
		assertThat(userRepository.existsByEmail("outro@example.com")).isFalse();
	}

	@Test
	void emailMustBeUnique() {
		userRepository.saveAndFlush(new User("Carla", "carla@example.com", "hashed-password"));

		assertThatThrownBy(() ->
			userRepository.saveAndFlush(new User("Carla 2", "carla@example.com", "outro-hash"))
		).isInstanceOf(DataIntegrityViolationException.class);
	}
}
