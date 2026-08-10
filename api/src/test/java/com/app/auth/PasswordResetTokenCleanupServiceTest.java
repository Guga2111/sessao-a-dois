package com.app.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetTokenCleanupServiceTest {

	@Mock
	private PasswordResetTokenRepository passwordResetTokenRepository;

	@Test
	void removesExpiredOrUsedTokensAndLogsCount() {
		when(passwordResetTokenRepository.deleteExpiredOrUsed(any())).thenReturn(4);
		PasswordResetTokenCleanupService service = new PasswordResetTokenCleanupService(passwordResetTokenRepository);

		service.removeExpiredOrUsedTokens();

		ArgumentCaptor<Instant> nowCaptor = ArgumentCaptor.forClass(Instant.class);
		verify(passwordResetTokenRepository).deleteExpiredOrUsed(nowCaptor.capture());

		assertThat(nowCaptor.getValue()).isBetween(Instant.now().minusSeconds(60), Instant.now().plusSeconds(60));
	}
}
