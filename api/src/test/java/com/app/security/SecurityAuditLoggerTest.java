package com.app.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAuditLoggerTest {

	private static final String SENHA_SECRETA = "senha-super-secreta-123";
	private static final String TOKEN_CLARO = "raw-refresh-token-value";
	private static final String HASH_TOKEN = "3f8a9c...deadbeef";
	private static final String CODIGO_CONVITE = "AB23CD45";

	private SecurityAuditLogger securityAuditLogger;
	private ListAppender<ILoggingEvent> logAppender;
	private Logger auditLogger;

	@BeforeEach
	void setUp() {
		securityAuditLogger = new SecurityAuditLogger();

		logAppender = new ListAppender<>();
		logAppender.start();
		auditLogger = (Logger) LoggerFactory.getLogger("security.audit");
		auditLogger.addAppender(logAppender);
	}

	@AfterEach
	void tearDown() {
		auditLogger.detachAppender(logAppender);
		MDC.clear();
	}

	@Test
	void logsLoginSuccessWithCorrelationId() {
		MDC.put(CorrelationIdFilter.MDC_KEY, "corr-123");
		UUID userId = UUID.randomUUID();

		securityAuditLogger.loginSuccess(userId, "ana@example.com", "203.0.113.5");

		String message = onlyMessage();
		assertThat(message).contains("event=login_success")
			.contains("correlationId=corr-123")
			.contains("userId=\"" + userId + "\"")
			.contains("email=\"ana@example.com\"")
			.contains("ip=\"203.0.113.5\"");
	}

	@Test
	void logsLoginFailureWithEmailAndIp() {
		securityAuditLogger.loginFailure("desconhecido@example.com", "203.0.113.9");

		String message = onlyMessage();
		assertThat(message).contains("event=login_failure")
			.contains("email=\"desconhecido@example.com\"")
			.contains("ip=\"203.0.113.9\"");
	}

	@Test
	void logsRefreshReuseDetection() {
		UUID userId = UUID.randomUUID();

		securityAuditLogger.refreshReuseDetected(userId, "203.0.113.7");

		String message = onlyMessage();
		assertThat(message).contains("event=refresh_reuse_detected")
			.contains("userId=\"" + userId + "\"")
			.contains("ip=\"203.0.113.7\"");
	}

	/** Epico 9, US-005: a linha registra QUAIS campos mudaram, nunca o valor novo do e-mail. */
	@Test
	void logsProfileUpdateWithFieldNamesButNeverTheNewEmailValue() {
		UUID userId = UUID.randomUUID();

		securityAuditLogger.profileUpdated(userId, List.of("name", "email"));

		String message = onlyMessage();
		assertThat(message).contains("event=profile_updated")
			.contains("userId=\"" + userId + "\"")
			.contains("fields=\"name,email\"")
			.doesNotContain("@");
	}

	/** Epico 9, US-007: o e-mail e justamente o dado que o titular pediu para eliminar. */
	@Test
	void logsAccountDeletionWithTheUserIdAndNoEmail() {
		UUID userId = UUID.randomUUID();

		securityAuditLogger.accountDeleted(userId);

		String message = onlyMessage();
		assertThat(message).contains("event=account_deleted")
			.contains("userId=\"" + userId + "\"")
			.doesNotContain("@");
	}

	/** Epico 10, US-006: o e-mail completo nunca deve chegar ao log, so a versao mascarada. */
	@Test
	void logsPasswordResetRequestedWithAMaskedEmail() {
		securityAuditLogger.passwordResetRequested("ana@example.com");

		String message = onlyMessage();
		assertThat(message).contains("event=password_reset_requested")
			.contains("email=\"a***@example.com\"")
			.doesNotContain("ana@example.com");
	}

	@Test
	void noAuditEntryEverContainsPasswordTokenHashOrInviteCodeValues() {
		UUID userId = UUID.randomUUID();
		UUID coupleId = UUID.randomUUID();

		securityAuditLogger.loginSuccess(userId, "ana@example.com", "203.0.113.5");
		securityAuditLogger.loginFailure("ana@example.com", "203.0.113.5");
		securityAuditLogger.logout(userId);
		securityAuditLogger.refresh(userId);
		securityAuditLogger.refreshReuseDetected(userId, "203.0.113.5");
		securityAuditLogger.coupleCreated(userId, coupleId);
		securityAuditLogger.coupleJoined(userId, coupleId);
		securityAuditLogger.coupleDissolved(userId, coupleId);
		securityAuditLogger.inviteCodeRegenerated(userId, coupleId);
		securityAuditLogger.profileUpdated(userId, List.of("name", "email"));
		securityAuditLogger.passwordChanged(userId);
		securityAuditLogger.accountDeleted(userId);
		securityAuditLogger.rateLimitExceeded("/api/auth/login", "203.0.113.5");
		securityAuditLogger.passwordResetRequested("ana@example.com");

		List<String> messages = logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
		assertThat(messages).isNotEmpty();
		for (String message : messages) {
			assertThat(message).doesNotContain(SENHA_SECRETA)
				.doesNotContain(TOKEN_CLARO)
				.doesNotContain(HASH_TOKEN)
				.doesNotContain(CODIGO_CONVITE);
		}
	}

	private String onlyMessage() {
		assertThat(logAppender.list).hasSize(1);
		return logAppender.list.get(0).getFormattedMessage();
	}
}
