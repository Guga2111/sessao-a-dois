package com.app.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.HtmlUtils;

/**
 * Adaptador de EmailSender sobre a API do Resend. So fica ativo com
 * app.email.provider=resend; texto simples mais HTML minimo derivado do mesmo
 * conteudo, sem template engine e sem imagens.
 */
@Component
@ConditionalOnProperty(prefix = "app.email", name = "provider", havingValue = "resend")
public class ResendEmailSender implements EmailSender {

	private static final String EMAILS_ENDPOINT = "/emails";

	private final RestClient resendRestClient;

	private final EmailProperties emailProperties;

	public ResendEmailSender(RestClient resendRestClient, EmailProperties emailProperties) {
		this.resendRestClient = resendRestClient;
		this.emailProperties = emailProperties;
	}

	@Override
	public void sendPasswordReset(String toEmail, String recipientName, String resetLink) {
		String subject = "Redefinicao de senha - Sessao a Dois";
		String text = "Ola " + recipientName + ",\n\n"
			+ "Recebemos um pedido para redefinir sua senha. Use o link abaixo:\n" + resetLink
			+ "\n\nSe voce nao pediu isso, ignore este e-mail.";
		String html = "<p>Ola " + HtmlUtils.htmlEscape(recipientName) + ",</p>"
			+ "<p>Recebemos um pedido para redefinir sua senha. Use o link abaixo:</p>"
			+ "<p><a href=\"" + HtmlUtils.htmlEscape(resetLink) + "\">" + HtmlUtils.htmlEscape(resetLink) + "</a></p>"
			+ "<p>Se voce nao pediu isso, ignore este e-mail.</p>";
		send(toEmail, subject, text, html);
	}

	@Override
	public void sendPasswordChangedNotice(String toEmail, String recipientName) {
		String subject = "Sua senha foi alterada - Sessao a Dois";
		String text = "Ola " + recipientName + ",\n\nSua senha foi alterada com sucesso.";
		String html = "<p>Ola " + HtmlUtils.htmlEscape(recipientName) + ",</p>"
			+ "<p>Sua senha foi alterada com sucesso.</p>";
		send(toEmail, subject, text, html);
	}

	private void send(String toEmail, String subject, String text, String html) {
		ResendEmailRequest payload = new ResendEmailRequest(emailProperties.getFrom(), toEmail, subject, text, html);
		try {
			resendRestClient.post()
				.uri(EMAILS_ENDPOINT)
				.body(payload)
				.retrieve()
				.toBodilessEntity();
		}
		catch (RestClientResponseException | ResourceAccessException ex) {
			throw new EmailDeliveryException("Falha ao enviar e-mail via Resend", ex);
		}
	}
}
