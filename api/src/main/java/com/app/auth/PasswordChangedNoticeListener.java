package com.app.auth;

import com.app.email.EmailSender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * US-010: avisa o titular quando a senha muda, so depois do commit (AFTER_COMMIT) - nunca sobre
 * uma troca que deu rollback. Falha do provedor e capturada aqui: ninguem mais esta esperando
 * essa chamada, e o request que disparou o evento ja respondeu (ou esta respondendo) 204.
 */
@Component
public class PasswordChangedNoticeListener {

	private static final Logger log = LoggerFactory.getLogger(PasswordChangedNoticeListener.class);

	private final EmailSender emailSender;

	public PasswordChangedNoticeListener(EmailSender emailSender) {
		this.emailSender = emailSender;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onPasswordChanged(PasswordChangedNoticeEvent event) {
		try {
			emailSender.sendPasswordChangedNotice(event.email(), event.name());
		}
		catch (RuntimeException ex) {
			log.warn("falha ao enviar aviso de troca de senha para o usuario {}", event.userId(), ex);
		}
	}
}
