package com.app.auth;

import com.app.email.EmailSender;
import com.app.user.User;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Emite o token e despacha o e-mail de redefinicao de senha fora do caminho de resposta
 * de {@code POST /api/auth/forgot-password} (US-006). Precisa ser um bean/metodo publico
 * separado do AuthService: @Async so funciona atraves do proxy do Spring, e uma chamada
 * de dentro da propria classe (self-invocation) ignoraria o proxy e rodaria em linha.
 */
@Component
public class PasswordResetDispatcher {

	private static final Logger log = LoggerFactory.getLogger(PasswordResetDispatcher.class);

	private final PasswordResetService passwordResetService;
	private final EmailSender emailSender;

	public PasswordResetDispatcher(PasswordResetService passwordResetService, EmailSender emailSender) {
		this.passwordResetService = passwordResetService;
		this.emailSender = emailSender;
	}

	/**
	 * Falha do EmailSender nao pode escapar (roda numa thread do executor, sem chamador
	 * esperando) nem desfazer o token, ja emitido e commitado por {@code issueResetLink}
	 * antes do envio ser tentado. So o id do usuario vai para o log - nunca o token.
	 */
	@Async(PasswordResetAsyncConfig.TASK_EXECUTOR_BEAN_NAME)
	public void dispatch(User user) {
		try {
			String resetLink = passwordResetService.issueResetLink(user);
			emailSender.sendPasswordReset(user.getEmail(), user.getName(), resetLink);
		}
		catch (RuntimeException ex) {
			log.warn("falha ao despachar e-mail de redefinicao de senha para userId={}", user.getId(), ex);
		}
	}
}
