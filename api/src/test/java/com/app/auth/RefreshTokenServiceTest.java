package com.app.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

	@Mock
	private RefreshTokenRepository refreshTokenRepository;

	private RefreshTokenService refreshTokenService;

	@Test
	void issueReturnsRawValueDifferentFromPersistedHash() {
		refreshTokenService = new RefreshTokenService(refreshTokenRepository, Duration.ofDays(30));
		UUID userId = UUID.randomUUID();

		when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

		String rawToken = refreshTokenService.issue(userId, "Mozilla/5.0", "203.0.113.5");

		ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
		verify(refreshTokenRepository).save(captor.capture());

		RefreshToken persisted = captor.getValue();
		assertThat(rawToken).isNotBlank();
		assertThat(persisted.getTokenHash()).isNotEqualTo(rawToken);
		assertThat(persisted.getTokenHash()).hasSize(64);
		assertThat(persisted.getUserId()).isEqualTo(userId);
		assertThat(persisted.getUserAgent()).isEqualTo("Mozilla/5.0");
		assertThat(persisted.getIp()).isEqualTo("203.0.113.5");
		assertThat(persisted.getExpiresAt()).isAfter(Instant.now().plus(Duration.ofDays(29)));
	}

	@Test
	void findActiveReturnsTokenWhenNotRevokedAndNotExpired() {
		refreshTokenService = new RefreshTokenService(refreshTokenRepository, Duration.ofDays(30));
		String rawToken = "raw-token-value";
		RefreshToken stored = mock(RefreshToken.class);
		when(stored.isActive()).thenReturn(true);
		when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(stored));

		Optional<RefreshToken> result = refreshTokenService.findActive(rawToken);

		assertThat(result).contains(stored);
	}

	@Test
	void findActiveReturnsEmptyWhenExpired() {
		refreshTokenService = new RefreshTokenService(refreshTokenRepository, Duration.ofDays(30));
		RefreshToken stored = mock(RefreshToken.class);
		when(stored.isActive()).thenReturn(false);
		when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(stored));

		Optional<RefreshToken> result = refreshTokenService.findActive("raw-token-value");

		assertThat(result).isEmpty();
	}

	@Test
	void findActiveReturnsEmptyWhenRevoked() {
		refreshTokenService = new RefreshTokenService(refreshTokenRepository, Duration.ofDays(30));
		RefreshToken stored = mock(RefreshToken.class);
		when(stored.isActive()).thenReturn(false);
		when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(stored));

		Optional<RefreshToken> result = refreshTokenService.findActive("raw-token-value");

		assertThat(result).isEmpty();
	}

	@Test
	void findActiveReturnsEmptyWhenTokenDoesNotExist() {
		refreshTokenService = new RefreshTokenService(refreshTokenRepository, Duration.ofDays(30));
		when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

		Optional<RefreshToken> result = refreshTokenService.findActive("unknown-token");

		assertThat(result).isEmpty();
	}

	@Test
	void revokeFamilyDelegatesToSingleUpdateQuery() {
		refreshTokenService = new RefreshTokenService(refreshTokenRepository, Duration.ofDays(30));
		UUID userId = UUID.randomUUID();

		refreshTokenService.revokeFamily(userId);

		verify(refreshTokenRepository).revokeAllActiveByUserId(eq(userId), any(Instant.class));
	}
}
