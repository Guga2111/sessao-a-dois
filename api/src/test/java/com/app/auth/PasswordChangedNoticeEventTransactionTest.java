package com.app.auth;

import com.app.email.EmailSender;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Prova o requisito central da US-010 que nenhum teste de unidade alcanca: o
 * PasswordChangedNoticeListener SO roda depois do commit real de uma transacao Spring, nunca
 * quando ela reverte - com um ApplicationEventPublisher/PlatformTransactionManager de verdade,
 * nao um mock.
 */
@SpringBootTest
class PasswordChangedNoticeEventTransactionTest {

	@Autowired
	private ApplicationEventPublisher eventPublisher;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@MockitoBean
	private EmailSender emailSender;

	@Test
	void doesNotSendTheNoticeWhenTheTransactionRollsBack() {
		TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
		PasswordChangedNoticeEvent event = new PasswordChangedNoticeEvent(UUID.randomUUID(), "ana@example.com", "Ana");

		transactionTemplate.execute(status -> {
			eventPublisher.publishEvent(event);
			status.setRollbackOnly();
			return null;
		});

		verifyNoInteractions(emailSender);
	}

	@Test
	void sendsTheNoticeAfterTheTransactionCommits() {
		TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
		PasswordChangedNoticeEvent event = new PasswordChangedNoticeEvent(UUID.randomUUID(), "ana@example.com", "Ana");

		transactionTemplate.execute(status -> {
			eventPublisher.publishEvent(event);
			return null;
		});

		verify(emailSender).sendPasswordChangedNotice("ana@example.com", "Ana");
	}
}
