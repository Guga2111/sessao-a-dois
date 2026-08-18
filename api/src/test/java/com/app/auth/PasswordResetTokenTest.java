package com.app.auth;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetTokenTest {

	@Test
	void isExpiredIsFalseBeforeExpiryAndTrueAfter() {
		Instant now = Instant.now();
		PasswordResetToken token = new PasswordResetToken(UUID.randomUUID(), "hash", now.plus(30, ChronoUnit.MINUTES));

		assertThat(token.isExpired(now)).isFalse();
		assertThat(token.isExpired(now.plus(31, ChronoUnit.MINUTES))).isTrue();
	}

	@Test
	void isUsedReflectsUsedAt() {
		Instant now = Instant.now();
		PasswordResetToken token = new PasswordResetToken(UUID.randomUUID(), "hash", now.plus(30, ChronoUnit.MINUTES));

		assertThat(token.isUsed()).isFalse();

		token.markUsed(now);

		assertThat(token.isUsed()).isTrue();
		assertThat(token.getUsedAt()).isEqualTo(now);
	}

	@Test
	void constructorSetsCreatedAt() {
		PasswordResetToken token = new PasswordResetToken(UUID.randomUUID(), "hash", Instant.now().plus(30, ChronoUnit.MINUTES));

		assertThat(token.getCreatedAt()).isNotNull();
	}
}
