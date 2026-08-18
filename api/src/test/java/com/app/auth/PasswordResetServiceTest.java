package com.app.auth;

import com.app.user.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

	@Mock
	private PasswordResetTokenRepository passwordResetTokenRepository;

	private PasswordResetService passwordResetService;

	@BeforeEach
	void setUp() {
		passwordResetService = new PasswordResetService(passwordResetTokenRepository, Duration.ofMinutes(30),
			"https://sessaoadois.luisgosampaio.com");
	}

	private User user() {
		User user = new User("Ana", "ana@example.com", "hashed");
		org.springframework.test.util.ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
		return user;
	}

	@Test
	void issueResetLinkPersistsOnlyTheHashOfTheToken() {
		User user = user();

		String link = passwordResetService.issueResetLink(user);

		ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
		verify(passwordResetTokenRepository).save(captor.capture());
		PasswordResetToken saved = captor.getValue();

		String rawToken = extractToken(link);
		assertThat(saved.getTokenHash()).isEqualTo(sha256Hex(rawToken));
		assertThat(saved.getTokenHash()).isNotEqualTo(rawToken);
		assertThat(saved.getUserId()).isEqualTo(user.getId());
	}

	@Test
	void issueResetLinkBuildsLinkWithPublicUrlAndRawToken() {
		User user = user();

		String link = passwordResetService.issueResetLink(user);

		assertThat(link).startsWith("https://sessaoadois.luisgosampaio.com/redefinir-senha?token=");
		String rawToken = extractToken(link);
		assertThat(rawToken).isNotBlank();
		assertThat(rawToken).doesNotContain("+", "/");
	}

	@Test
	void issueResetLinkAppliesConfiguredTtl() {
		User user = user();
		Instant before = Instant.now();

		passwordResetService.issueResetLink(user);

		Instant after = Instant.now();
		ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
		verify(passwordResetTokenRepository).save(captor.capture());
		Instant expiresAt = captor.getValue().getExpiresAt();

		assertThat(expiresAt).isAfter(before.plus(Duration.ofMinutes(30)).minusSeconds(2));
		assertThat(expiresAt).isBefore(after.plus(Duration.ofMinutes(30)).plusSeconds(2));
	}

	@Test
	void issueResetLinkInvalidatesAllPreviousTokensOfTheUser() {
		User user = user();

		passwordResetService.issueResetLink(user);

		verify(passwordResetTokenRepository).invalidateAllActiveByUserId(eq(user.getId()), any(Instant.class));
	}

	@Test
	void consumeTokenReturnsTheUserIdAndMarksTheTokenUsed() {
		UUID userId = UUID.randomUUID();
		PasswordResetToken token = new PasswordResetToken(userId, sha256Hex("raw-token"),
			Instant.now().plus(Duration.ofMinutes(30)));
		when(passwordResetTokenRepository.findByTokenHash(sha256Hex("raw-token"))).thenReturn(java.util.Optional.of(token));

		UUID result = passwordResetService.consumeToken("raw-token");

		assertThat(result).isEqualTo(userId);
		assertThat(token.isUsed()).isTrue();
	}

	@Test
	void consumeTokenRejectsUnknownToken() {
		when(passwordResetTokenRepository.findByTokenHash(any())).thenReturn(java.util.Optional.empty());

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> passwordResetService.consumeToken("nao-existe"))
			.isInstanceOf(InvalidPasswordResetTokenException.class);
	}

	@Test
	void consumeTokenRejectsExpiredToken() {
		PasswordResetToken token = new PasswordResetToken(UUID.randomUUID(), sha256Hex("raw-token"),
			Instant.now().minus(Duration.ofMinutes(1)));
		when(passwordResetTokenRepository.findByTokenHash(sha256Hex("raw-token"))).thenReturn(java.util.Optional.of(token));

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> passwordResetService.consumeToken("raw-token"))
			.isInstanceOf(InvalidPasswordResetTokenException.class);
	}

	@Test
	void consumeTokenRejectsAlreadyUsedToken() {
		PasswordResetToken token = new PasswordResetToken(UUID.randomUUID(), sha256Hex("raw-token"),
			Instant.now().plus(Duration.ofMinutes(30)));
		token.markUsed(Instant.now());
		when(passwordResetTokenRepository.findByTokenHash(sha256Hex("raw-token"))).thenReturn(java.util.Optional.of(token));

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> passwordResetService.consumeToken("raw-token"))
			.isInstanceOf(InvalidPasswordResetTokenException.class);
	}

	private static String extractToken(String link) {
		return link.substring(link.indexOf("token=") + "token=".length());
	}

	private static String sha256Hex(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		}
		catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
}
