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
		securityAuditLogger.inviteCodeRegenerated(userId, coupleId);
		securityAuditLogger.rateLimitExceeded("/api/auth/login", "203.0.113.5");

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
