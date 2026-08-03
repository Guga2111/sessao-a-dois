package com.app.websocket;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.app.couple.Couple;
import com.app.couple.CoupleService;

/**
 * Decide se a sessao WebSocket autenticada do usuario pode assinar um destino
 * {@code /topic/couple/{id}/**}: so o proprio casal do usuario (via {@link
 * CoupleService#getCurrentCouple}) pode assinar seus proprios topicos. Usada
 * pela US-007 para ligar essa decisao ao {@code AuthorizationManager} de
 * mensagens de entrada do broker STOMP - este componente nao depende de
 * nenhum tipo do Spring Security Messaging, apenas do destino e dos
 * atributos de sessao gravados no handshake por {@link JwtHandshakeInterceptor}.
 */
@Component
public class CoupleDestinationAuthorizationManager {

	private static final Pattern COUPLE_TOPIC_PATTERN = Pattern.compile("^/topic/couple/([^/]+)/.+$");

	private final CoupleService coupleService;

	public CoupleDestinationAuthorizationManager(CoupleService coupleService) {
		this.coupleService = coupleService;
	}

	public boolean isAuthorized(String destination, Map<String, Object> sessionAttributes) {
		UUID userId = extractUserId(sessionAttributes);
		if (userId == null || destination == null) {
			return false;
		}

		Matcher matcher = COUPLE_TOPIC_PATTERN.matcher(destination);
		if (!matcher.matches()) {
			return false;
		}

		UUID destinationCoupleId = parseUuid(matcher.group(1));
		if (destinationCoupleId == null) {
			return false;
		}

		return coupleService.getCurrentCouple(userId)
			.map(Couple::getId)
			.map(destinationCoupleId::equals)
			.orElse(false);
	}

	private UUID extractUserId(Map<String, Object> sessionAttributes) {
		if (sessionAttributes == null) {
			return null;
		}
		Object value = sessionAttributes.get(JwtHandshakeInterceptor.USER_ID_ATTRIBUTE);
		return (value instanceof UUID userId) ? userId : null;
	}

	private UUID parseUuid(String raw) {
		try {
			return UUID.fromString(raw);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
