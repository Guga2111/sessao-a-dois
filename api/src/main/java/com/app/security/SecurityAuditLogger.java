package com.app.security;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Unico ponto de escrita no logger dedicado {@code security.audit}, separado
 * do log de aplicacao (US-012). Formata cada evento como uma linha
 * chave=valor (evento, correlation id da US-011, e os campos proprios do
 * evento) para que nenhum ponto de emissao (AuthService, CoupleService,
 * RefreshTokenService, RateLimitFilter) precise montar a string de log na
 * mao.
 *
 * <p>NENHUM metodo aqui aceita senha, token em claro, hash de token ou o
 * valor do codigo de convite - so identificadores (id de usuario/casal,
 * e-mail, IP, endpoint). O nivel deste logger e configuravel
 * independentemente do resto da aplicacao via {@code logging.level.security.audit}.
 */
@Component
public class SecurityAuditLogger {

	private static final Logger AUDIT_LOG = LoggerFactory.getLogger("security.audit");

	public void loginSuccess(UUID userId, String email, String ip) {
		log("login_success", fields("userId", userId, "email", email, "ip", ip));
	}

	public void loginFailure(String email, String ip) {
		log("login_failure", fields("email", email, "ip", ip));
	}

	public void logout(UUID userId) {
		log("logout", fields("userId", userId));
	}

	public void refresh(UUID userId) {
		log("refresh", fields("userId", userId));
	}

	public void refreshReuseDetected(UUID userId, String ip) {
		log("refresh_reuse_detected", fields("userId", userId, "ip", ip));
	}

	public void coupleCreated(UUID userId, UUID coupleId) {
		log("couple_created", fields("userId", userId, "coupleId", coupleId));
	}

	public void coupleJoined(UUID userId, UUID coupleId) {
		log("couple_joined", fields("userId", userId, "coupleId", coupleId));
	}

	public void coupleDissolved(UUID userId, UUID coupleId) {
		log("couple_dissolved", fields("userId", userId, "coupleId", coupleId));
	}

	public void inviteCodeRegenerated(UUID userId, UUID coupleId) {
		log("invite_code_regenerated", fields("userId", userId, "coupleId", coupleId));
	}

	public void rateLimitExceeded(String endpoint, String ip) {
		log("rate_limit_exceeded", fields("endpoint", endpoint, "ip", ip));
	}

	private static Map<String, Object> fields(Object... keyValues) {
		Map<String, Object> fields = new LinkedHashMap<>();
		for (int i = 0; i < keyValues.length; i += 2) {
			fields.put((String) keyValues[i], keyValues[i + 1]);
		}
		return fields;
	}

	private void log(String event, Map<String, Object> fields) {
		StringBuilder line = new StringBuilder("event=").append(event);
		String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
		line.append(" correlationId=").append(correlationId == null ? "-" : correlationId);
		for (Map.Entry<String, Object> entry : fields.entrySet()) {
			line.append(' ').append(entry.getKey()).append('=').append(quote(entry.getValue()));
		}
		AUDIT_LOG.info(line.toString());
	}

	private static String quote(Object value) {
		return "\"" + (value == null ? "" : value.toString()) + "\"";
	}
}
