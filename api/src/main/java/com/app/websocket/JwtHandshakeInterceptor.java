package com.app.websocket;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.WebUtils;

import com.app.auth.AuthCookieService;
import com.app.security.JwtService;

import io.jsonwebtoken.JwtException;

import jakarta.servlet.http.Cookie;

/**
 * Valida o JWT do usuario antes de aceitar o handshake STOMP/SockJS: o token
 * chega via cookie {@code access_token} (mesmo cookie HttpOnly usado pelo
 * resto da API, ver {@link JwtService} e {@code JwtAuthenticationFilter}) e,
 * se ausente ou invalido, o handshake e rejeitado com 401 antes de qualquer
 * conexao ser aberta. Sem fallback para o antigo query param {@code ?token=}
 * (mesma decisao D1 do Epico 4 aplicada na US-005).
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
		Cookie cookie = WebUtils.getCookie(servletRequest.getServletRequest(), AuthCookieService.ACCESS_TOKEN_COOKIE);
		if (cookie == null || !StringUtils.hasText(cookie.getValue())) {
			return null;
		}
		return cookie.getValue();
	}
}
