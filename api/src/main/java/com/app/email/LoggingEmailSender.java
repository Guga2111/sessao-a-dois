package com.app.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Implementacao no-op de EmailSender: so loga, nunca envia de verdade. E o default
 * (app.email.provider=log), usado em dev e em toda a suite de testes, para que nada
 * dependa de rede ou credencial de provedor.
 */
@Component
@ConditionalOnProperty(prefix = "app.email", name = "provider", havingValue = "log", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

	private static final int LINK_TRUNCATE_LENGTH = 20;

	@Override
	public void sendPasswordReset(String toEmail, String recipientName, String resetLink) {
		log.info("E-mail de recuperacao de senha (no-op) para {}", toEmail);
		log.debug("Link de recuperacao (apenas debug): {}", resetLink);
		if (log.isInfoEnabled() && !log.isDebugEnabled()) {
			log.info("Link truncado: {}...", truncate(resetLink));
		}
	}

	@Override
	public void sendPasswordChangedNotice(String toEmail, String recipientName) {
		log.info("E-mail de aviso de senha alterada (no-op) para {}", toEmail);
	}

	private String truncate(String value) {
		if (value == null || value.length() <= LINK_TRUNCATE_LENGTH) {
			return value;
		}
		return value.substring(0, LINK_TRUNCATE_LENGTH);
	}
}
