package com.app.websocket;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import com.app.security.JwtService;

import io.jsonwebtoken.JwtException;

/**
 * Valida o JWT do usuario antes de aceitar o handshake STOMP/SockJS: o token
 * chega via query param {@code ?token=} (SockJS/browsers nao permitem
 * customizar headers no handshake HTTP inicial) e, se ausente ou invalido, o
 * handshake e rejeitado com 401 antes de qualquer conexao ser aberta.
 */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

	public static final String USER_ID_ATTRIBUTE = "userId";

	private final JwtService jwtService;

	public JwtHandshakeInterceptor(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@Override
	public boolean beforeHandshake(
			ServerHttpRequest request,
			ServerHttpResponse response,
			WebSocketHandler wsHandler,
			Map<String, Object> attributes) {
		String token = extractToken(request);
		if (token == null) {
			response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
			return false;
		}

		try {
			UUID userId = jwtService.parseSubject(token);
			attributes.put(USER_ID_ATTRIBUTE, userId);
			return true;
		} catch (JwtException | IllegalArgumentException e) {
			response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
			return false;
		}
	}

	@Override
	public void afterHandshake(
			ServerHttpRequest request,
			ServerHttpResponse response,
			WebSocketHandler wsHandler,
			Exception exception) {
		// no-op
	}

	private String extractToken(ServerHttpRequest request) {
		if (!(request instanceof ServletServerHttpRequest servletRequest)) {
			return null;
		}
		List<String> values = UriComponentsBuilder.fromUri(servletRequest.getURI())
			.build()
			.getQueryParams()
			.get("token");
		if (values == null || values.isEmpty()) {
			return null;
		}
		String token = values.get(0);
		return (token == null || token.isBlank()) ? null : token;
	}
}
