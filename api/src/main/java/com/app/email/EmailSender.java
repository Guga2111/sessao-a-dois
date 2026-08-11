package com.app.email;

/**
 * Porta de e-mail transacional. Unica superficie publica do pacote com.app.email -
 * nenhuma outra feature deve conhecer o provedor por tras dela.
 */
public interface EmailSender {

	void sendPasswordReset(String toEmail, String recipientName, String resetLink);

	void sendPasswordChangedNotice(String toEmail, String recipientName);
}
