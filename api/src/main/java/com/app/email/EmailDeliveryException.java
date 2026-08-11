package com.app.email;

/**
 * Falha ao entregar um e-mail atraves do provedor configurado (erro HTTP 4xx/5xx ou
 * timeout). Nenhum tipo do cliente HTTP escapa do pacote com.app.email - quem chama
 * a porta EmailSender so precisa conhecer esta excecao.
 */
public class EmailDeliveryException extends RuntimeException {

	public EmailDeliveryException(String message, Throwable cause) {
		super(message, cause);
	}
}
