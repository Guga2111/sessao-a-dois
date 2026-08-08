package com.app.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

	@Mock
	private RefreshTokenRepository refreshTokenRepository;

	private RefreshTokenService refreshTokenService;

	@Test
	void issueReturnsRawValueDifferentFromPersistedHash() {
		refreshTokenService = new RefreshTokenService(refreshTokenRepository, Duration.ofDays(30), Duration.ofSeconds(30), new com.app.security.SecurityAuditLogger());
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
		refreshTokenService = new RefreshTokenService(refreshTokenRepository, Duration.ofDays(30), Duration.ofSeconds(30), new com.app.security.SecurityAuditLogger());
		String rawToken = "raw-token-value";
		RefreshToken stored = mock(RefreshToken.class);
		when(stored.isActive()).thenReturn(true);
		when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(stored));

		Optional<RefreshToken> result = refreshTokenService.findActive(rawToken);

		assertThat(result).contains(stored);
	}

	@Test
	void findActiveReturnsEmptyWhenExpired() {
		refreshTokenService = new RefreshTokenService(refreshTokenRepository, Duration.ofDays(30), Duration.ofSeconds(30), new com.app.security.SecurityAuditLogger());
		RefreshToken stored = mock(RefreshToken.class);
		when(stored.isActive()).thenReturn(false);
		when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(stored));

		Optional<RefreshToken> result = refreshTokenService.findActive("raw-token-value");

		assertThat(result).isEmpty();
	}

	@Test
	void findActiveReturnsEmptyWhenRevoked() {
		refreshTokenService = new RefreshTokenService(refreshTokenRepository, Duration.ofDays(30), Duration.ofSeconds(30), new com.app.security.SecurityAuditLogger());
		RefreshToken stored = mock(RefreshToken.class);
		when(stored.isActive()).thenReturn(false);
		when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(stored));

		Optional<RefreshToken> result = refreshTokenService.findActive("raw-token-value");

		assertThat(result).isEmpty();
	}

	@Test
	void findActiveReturnsEmptyWhenTokenDoesNotExist() {
		refreshTokenService = new RefreshTokenService(refreshTokenRepository, Duration.ofDays(30), Duration.ofSeconds(30), new com.app.security.SecurityAuditLogger());
		when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

		Optional<RefreshToken> result = refreshTokenService.findActive("unknown-token");

		assertThat(result).isEmpty();
	}

	@Test
	void revokeFamilyDelegatesToSingleUpdateQuery() {
		refreshTokenService = new RefreshTokenService(refreshTokenRepository, Duration.ofDays(30), Duration.ofSeconds(30), new com.app.security.SecurityAuditLogger());
		UUID userId = UUID.randomUUID();

		refreshTokenService.revokeFamily(userId);

		verify(refreshTokenRepository).revokeAllActiveByUserId(eq(userId), any(Instant.class));
	}

	/**
	 * Exclusao de conta (US-007): dois statements baseados em conjunto, nesta ordem - anular
	 * {@code replaced_by_id} e so entao apagar. Nunca {@code deleteAll(entidades)}, que emitiria um
	 * {@code DELETE} por linha e estouraria a auto-FK {@code fk_refresh_token_replaced_by}.
	 */
	@Test
	void deleteAllForUserBreaksTheRotationChainBeforeDeletingInSetBasedStatements() {
		refreshTokenService = new RefreshTokenService(refreshTokenRepository, Duration.ofDays(30), Duration.ofSeconds(30), new com.app.security.SecurityAuditLogger());
		UUID userId = UUID.randomUUID();

		refreshTokenService.deleteAllForUser(userId);

		InOrder inOrder = inOrder(refreshTokenRepository);
		inOrder.verify(refreshTokenRepository).clearReplacedByForUser(userId);
		inOrder.verify(refreshTokenRepository).deleteAllByUserId(userId);
		verify(refreshTokenRepository, never()).deleteAll(any(Iterable.class));
	}

	@Test
	void rotateSuccessfullyRevokesCurrentAndIssuesFreshSlidingTtl() {
		refreshTokenService = new RefreshTokenService(refreshTokenRepository, Duration.ofDays(30), Duration.ofSeconds(30), new com.app.security.SecurityAuditLogger());
		UUID userId = UUID.randomUUID();
		RefreshToken current = new RefreshToken(userId, "old-hash", Instant.now().plus(Duration.ofDays(2)), "UA", "1.2.3.4");
		ReflectionTestUtils.setField(current, "id", UUID.randomUUID());

		when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(current));
		when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
			RefreshToken saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
			return saved;
		});

		RefreshTokenService.RotationResult result = refreshTokenService.rotate("raw-old-token", "UA2", "5.6.7.8");

		assertThat(result.userId()).isEqualTo(userId);
		assertThat(result.refreshToken()).isNotBlank();
		assertThat(current.getRevokedAt()).isNotNull();
		assertThat(current.getReplacedById()).isNotNull();
		verify(refreshTokenRepository, never()).revokeAllActiveByUserId(any(), any());

		ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
		verify(refreshTokenRepository).save(captor.capture());
		assertThat(captor.getValue().getExpiresAt()).isAfter(Instant.now().plus(Duration.ofDays(29)));
	}

	@Test
	void rotateUnknownTokenIsRejected() {
		refreshTokenService = new RefreshTokenService(refreshTokenRepository, Duration.ofDays(30), Duration.ofSeconds(30), new com.app.security.SecurityAuditLogger());
		when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> refreshTokenService.rotate("unknown-raw-token", "UA", "1.2.3.4"))
			.isInstanceOf(InvalidRefreshTokenException.class);
	}

	@Test
	void rotateExpiredTokenIsRejectedWithoutRevokingFamily() {
		refreshTokenService = new RefreshTokenService(refreshTokenRepository, Duration.ofDays(30), Duration.ofSeconds(30), new com.app.security.SecurityAuditLogger());
		UUID userId = UUID.randomUUID();
		RefreshToken expired = new RefreshToken(userId, "hash", Instant.now().minus(Duration.ofMinutes(1)), "UA", "1.2.3.4");
		when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expired));

		assertThatThrownBy(() -> refreshTokenService.rotate("raw-token", "UA", "1.2.3.4"))
			.isInstanceOf(InvalidRefreshTokenException.class);

		verify(refreshTokenRepository, never()).revokeAllActiveByUserId(any(), any());
		verify(refreshTokenRepository, never()).save(any());
	}

	@Test
	void rotateReusedTokenOutsideGraceRevokesFamily() {
		refreshTokenService = new RefreshTokenService(refreshTokenRepository, Duration.ofDays(30), Duration.ofSeconds(30), new com.app.security.SecurityAuditLogger());
		UUID userId = UUID.randomUUID();
		RefreshToken revoked = new RefreshToken(userId, "hash", Instant.now().plus(Duration.ofDays(2)), "UA", "1.2.3.4");
		revoked.setRevokedAt(Instant.now().minus(Duration.ofSeconds(60)));
		when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(revoked));

		assertThatThrownBy(() -> refreshTokenService.rotate("reused-raw-token", "UA", "1.2.3.4"))
			.isInstanceOf(RefreshReuseDetectedException.class);

		verify(refreshTokenRepository).revokeAllActiveByUserId(eq(userId), any(Instant.class));
		verify(refreshTokenRepository, never()).save(any());
	}

	@Test
	void revokeMarksActiveTokenAsRevoked() {
		refreshTokenService = new RefreshTokenService(refreshTokenRepository, Duration.ofDays(30), Duration.ofSeconds(30), new com.app.security.SecurityAuditLogger());
		UUID userId = UUID.randomUUID();
		RefreshToken token = new RefreshToken(userId, "hash", Instant.now().plus(Duration.ofDays(2)), "UA", "1.2.3.4");
		when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

		refreshTokenService.revoke("raw-token");

		assertThat(token.getRevokedAt()).isNotNull();
	}

	@Test
	void revokeIsNoOpWhenTokenAlreadyRevoked() {
		refreshTokenService = new RefreshTokenService(refreshTokenRepository, Duration.ofDays(30), Duration.ofSeconds(30), new com.app.security.SecurityAuditLogger());
		UUID userId = UUID.randomUUID();
		RefreshToken token = new RefreshToken(userId, "hash", Instant.now().plus(Duration.ofDays(2)), "UA", "1.2.3.4");
		Instant firstRevocation = Instant.now().minus(Duration.ofMinutes(5));
		token.setRevokedAt(firstRevocation);
		when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

		refreshTokenService.revoke("raw-token");

		assertThat(token.getRevokedAt()).isEqualTo(firstRevocation);
	}

	@Test
	void revokeIsNoOpWhenTokenDoesNotExist() {
		refreshTokenService = new RefreshTokenService(refreshTokenRepository, Duration.ofDays(30), Duration.ofSeconds(30), new com.app.security.SecurityAuditLogger());
		when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

		refreshTokenService.revoke("unknown-token");
	}

	@Test
	void rotateReusedTokenWithinGraceFollowsSubstituteWithoutRevokingFamily() {
		refreshTokenService = new RefreshTokenService(refreshTokenRepository, Duration.ofDays(30), Duration.ofSeconds(30), new com.app.security.SecurityAuditLogger());
		UUID userId = UUID.randomUUID();
		UUID substituteId = UUID.randomUUID();

		RefreshToken revoked = new RefreshToken(userId, "old-hash", Instant.now().plus(Duration.ofDays(2)), "UA", "1.2.3.4");
		revoked.setRevokedAt(Instant.now().minus(Duration.ofSeconds(5)));
		revoked.setReplacedById(substituteId);

		RefreshToken substitute = new RefreshToken(userId, "sub-hash", Instant.now().plus(Duration.ofDays(30)), "UA2", "5.6.7.8");
		ReflectionTestUtils.setField(substitute, "id", substituteId);

		when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(revoked));
		when(refreshTokenRepository.findById(substituteId)).thenReturn(Optional.of(substitute));
		when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
			RefreshToken saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
			return saved;
		});

		RefreshTokenService.RotationResult result = refreshTokenService.rotate("raw-old-token", "UA3", "9.9.9.9");

		assertThat(result.userId()).isEqualTo(userId);
		assertThat(result.refreshToken()).isNotBlank();
		assertThat(substitute.getRevokedAt()).isNotNull();
		assertThat(substitute.getReplacedById()).isNotNull();
		verify(refreshTokenRepository, never()).revokeAllActiveByUserId(any(), any());
	}
}
