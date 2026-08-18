package com.app.auth;

import com.app.email.EmailSender;
import com.app.user.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Roda o metodo direto (nao via proxy @Async) - so a orquestracao token+e-mail e a
 * tolerancia a falha do provedor importam aqui; o comportamento assincrono em si vem
 * de pronto do Spring (PasswordResetAsyncConfig) e nao precisa ser reprovado por teste.
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetDispatcherTest {

	@Mock
	private PasswordResetService passwordResetService;

	@Mock
	private EmailSender emailSender;

	private PasswordResetDispatcher dispatcher;

	@BeforeEach
	void setUp() {
		this.dispatcher = new PasswordResetDispatcher(passwordResetService, emailSender);
	}

	@Test
	void issuesTheTokenAndSendsTheEmail() {
		User user = new User("Ana", "ana@example.com", "hash");
		when(passwordResetService.issueResetLink(user)).thenReturn("https://app.example/redefinir-senha?token=abc");

		dispatcher.dispatch(user);

		verify(emailSender).sendPasswordReset("ana@example.com", "Ana", "https://app.example/redefinir-senha?token=abc");
	}

	@Test
	void aProviderFailureDoesNotEscapeTheDispatch() {
		User user = new User("Ana", "ana@example.com", "hash");
		when(passwordResetService.issueResetLink(user)).thenReturn("https://app.example/redefinir-senha?token=abc");

		doThrow(new RuntimeException("provedor fora do ar"))
			.when(emailSender)
			.sendPasswordReset(anyString(), anyString(), anyString());

		dispatcher.dispatch(user);

		verify(passwordResetService).issueResetLink(user);
	}
}
