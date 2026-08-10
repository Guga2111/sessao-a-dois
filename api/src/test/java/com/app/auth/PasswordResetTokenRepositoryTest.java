package com.app.auth;

import com.app.user.User;
import com.app.user.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class PasswordResetTokenRepositoryTest {

	@Autowired
	private PasswordResetTokenRepository passwordResetTokenRepository;

	@Autowired
	private UserRepository userRepository;

	private User persistedUser() {
		return userRepository.save(new User("Ana", "ana-" + UUID.randomUUID() + "@example.com", "hashed"));
	}

	@Test
	void findByTokenHashFindsPersistedToken() {
		User user = persistedUser();
		passwordResetTokenRepository
			.save(new PasswordResetToken(user.getId(), "hash-1", Instant.now().plus(30, ChronoUnit.MINUTES)));

		assertThat(passwordResetTokenRepository.findByTokenHash("hash-1")).isPresent();
		assertThat(passwordResetTokenRepository.findByTokenHash("nao-existe")).isEmpty();
	}

	@Test
	void duplicateTokenHashViolatesUniqueConstraint() {
		User user = persistedUser();
		Instant expiresAt = Instant.now().plus(30, ChronoUnit.MINUTES);
		passwordResetTokenRepository.saveAndFlush(new PasswordResetToken(user.getId(), "hash-dup", expiresAt));

		assertThatThrownBy(() ->
			passwordResetTokenRepository.saveAndFlush(new PasswordResetToken(user.getId(), "hash-dup", expiresAt))
		).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void invalidateAllActiveByUserIdMarksOnlyActiveTokensAsUsed() {
		User user = persistedUser();
		Instant now = Instant.now();
		passwordResetTokenRepository
			.saveAndFlush(new PasswordResetToken(user.getId(), "hash-active", now.plus(30, ChronoUnit.MINUTES)));
		passwordResetTokenRepository
			.saveAndFlush(new PasswordResetToken(user.getId(), "hash-expired", now.minus(1, ChronoUnit.MINUTES)));

		int updated = passwordResetTokenRepository.invalidateAllActiveByUserId(user.getId(), now);

		assertThat(updated).isEqualTo(1);
		assertThat(passwordResetTokenRepository.findByTokenHash("hash-active").orElseThrow().getUsedAt()).isNotNull();
		assertThat(passwordResetTokenRepository.findByTokenHash("hash-expired").orElseThrow().getUsedAt()).isNull();
	}

	@Test
	void deleteExpiredOrUsedRemovesExpiredAndUsedButKeepsActive() {
		User user = persistedUser();
		Instant now = Instant.now();
		PasswordResetToken activeToken = new PasswordResetToken(user.getId(), "hash-keep", now.plus(30, ChronoUnit.MINUTES));
		PasswordResetToken expiredToken = new PasswordResetToken(user.getId(), "hash-gone-expired", now.minus(1, ChronoUnit.MINUTES));
		PasswordResetToken usedToken = new PasswordResetToken(user.getId(), "hash-gone-used", now.plus(30, ChronoUnit.MINUTES));
		usedToken.markUsed(now);
		passwordResetTokenRepository.save(activeToken);
		passwordResetTokenRepository.save(expiredToken);
		passwordResetTokenRepository.saveAndFlush(usedToken);

		int removed = passwordResetTokenRepository.deleteExpiredOrUsed(now);

		assertThat(removed).isEqualTo(2);
		assertThat(passwordResetTokenRepository.findByTokenHash("hash-keep")).isPresent();
		assertThat(passwordResetTokenRepository.findByTokenHash("hash-gone-expired")).isEmpty();
		assertThat(passwordResetTokenRepository.findByTokenHash("hash-gone-used")).isEmpty();
	}
}
