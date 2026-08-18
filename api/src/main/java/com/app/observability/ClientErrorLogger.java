package com.app.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import com.app.security.CorrelationIdFilter;

/**
 * Unico ponto de escrita da linha {@code event=client_error} (US-003), no
 * mesmo molde de {@code com.app.security.SecurityAuditLogger}: o controller
 * nao formata a linha de log na mao. O correlation id vem do MDC que o
 * {@link CorrelationIdFilter} ja preenche - nenhum mecanismo de correlacao
 * novo nasce aqui.
 *
 * <p>Entrada publica e nao autenticada: todo campo passa por {@link #sanitize}
 * antes de ir para o log, removendo quebra de linha e caractere de controle,
 * para que ninguem consiga injetar uma linha de log falsa (log forging).
 */
@Component
public class ClientErrorLogger {

	private static final Logger LOG = LoggerFactory.getLogger(ClientErrorLogger.class);

	public void log(ClientErrorRequest request) {
		String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);

		StringBuilder line = new StringBuilder("event=client_error");
		line.append(" correlationId=").append(correlationId == null ? "-" : correlationId);
		line.append(" route=").append(quote(sanitize(request.route())));
		line.append(" message=").append(quote(sanitize(request.message())));
		if (request.userAgent() != null) {
			line.append(" userAgent=").append(quote(sanitize(request.userAgent())));
		}
		if (request.stack() != null) {
			line.append(" stack=").append(quote(sanitize(request.stack())));
		}

		LOG.warn(line.toString());
	}

	private static String sanitize(String value) {
		if (value == null) {
			return null;
		}
		return value.replaceAll("[\\p{Cntrl}]", " ").trim();
	}

	private static String quote(String value) {
		return "\"" + (value == null ? "" : value) + "\"";
	}
}
