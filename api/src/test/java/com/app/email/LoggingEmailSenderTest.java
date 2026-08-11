package com.app.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class LoggingEmailSenderTest {

	private final LoggingEmailSender sender = new LoggingEmailSender();

	private ListAppender<ILoggingEvent> appender;

	private Logger logbackLogger;

	@BeforeEach
	void setUp() {
		logbackLogger = (Logger) LoggerFactory.getLogger(LoggingEmailSender.class);
		appender = new ListAppender<>();
		appender.start();
		logbackLogger.addAppender(appender);
		logbackLogger.setLevel(Level.INFO);
	}

	@AfterEach
	void tearDown() {
		logbackLogger.detachAppender(appender);
		logbackLogger.setLevel(null);
	}

	@Test
	void sendPasswordResetLogsRecipientButNeverTheFullLink() {
		String resetLink = "https://sessaoadois.luisgosampaio.com/redefinir-senha?token=super-secret-token-value";

		assertThatCode(() -> sender.sendPasswordReset("user@example.com", "Fulano", resetLink)).doesNotThrowAnyException();

		String logged = appender.list.stream()
			.map(ILoggingEvent::getFormattedMessage)
			.reduce("", (a, b) -> a + " " + b);

		assertThat(logged).contains("user@example.com");
		assertThat(logged).doesNotContain(resetLink);
		assertThat(logged).doesNotContain("super-secret-token-value");
	}

	@Test
	void sendPasswordChangedNoticeLogsRecipient() {
		assertThatCode(() -> sender.sendPasswordChangedNotice("user@example.com", "Fulano")).doesNotThrowAnyException();

		String logged = appender.list.stream()
			.map(ILoggingEvent::getFormattedMessage)
			.reduce("", (a, b) -> a + " " + b);

		assertThat(logged).contains("user@example.com");
	}
}
