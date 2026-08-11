package com.app.auth;

import com.app.email.EmailSender;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PasswordChangedNoticeListenerTest {

	@Mock
	private EmailSender emailSender;

	@Test
	void sendsThePasswordChangedNotice() {
		PasswordChangedNoticeListener listener = new PasswordChangedNoticeListener(emailSender);
		PasswordChangedNoticeEvent event = new PasswordChangedNoticeEvent(UUID.randomUUID(), "ana@example.com", "Ana");

		listener.onPasswordChanged(event);

		verify(emailSender).sendPasswordChangedNotice("ana@example.com", "Ana");
	}

	@Test
	void aFailingProviderDoesNotPropagate() {
		PasswordChangedNoticeListener listener = new PasswordChangedNoticeListener(emailSender);
		PasswordChangedNoticeEvent event = new PasswordChangedNoticeEvent(UUID.randomUUID(), "ana@example.com", "Ana");
		doThrow(new RuntimeException("provedor fora do ar")).when(emailSender)
			.sendPasswordChangedNotice("ana@example.com", "Ana");

		listener.onPasswordChanged(event);
	}
}
