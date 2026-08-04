package com.app.websocket;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.socket.WebSocketHandler;

import com.app.security.JwtService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre o unico portao de autenticacao do handshake STOMP/SockJS: token
 * ausente, em branco, invalido e valido, sob {@link JwtHandshakeInterceptor}.
 */
class JwtHandshakeInterceptorTest {

	private static final String VALID_SECRET = "a-valid-secret-with-at-least-32-bytes!!";
	private static final String OTHER_VALID_SECRET = "a-different-valid-secret-32-bytes-plus!!";

	private static final Duration TTL = Duration.ofMinutes(15);
	private static final String ISSUER = "sessao-a-dois";
	private static final String AUDIENCE = "sessao-a-dois-client";

	private final JwtService jwtService = new JwtService(VALID_SECRET, TTL, ISSUER, AUDIENCE);
	private final JwtHandshakeInterceptor interceptor = new JwtHandshakeInterceptor(jwtService);
	private final WebSocketHandler wsHandler = Mockito.mock(WebSocketHandler.class);

	@Test
	void rejectsHandshakeWithoutTokenQueryParam() {
		ServerHttpRequest request = requestWithQuery(null);
		ServerHttpResponse response = Mockito.mock(ServerHttpResponse.class);
		Map<String, Object> attributes = new HashMap<>();

		boolean accepted = interceptor.beforeHandshake(request, response, wsHandler, attributes);

		assertThat(accepted).isFalse();
		Mockito.verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
		assertThat(attributes).doesNotContainKey(JwtHandshakeInterceptor.USER_ID_ATTRIBUTE);
	}

	@Test
	void rejectsHandshakeWithEmptyOrBlankTokenQueryParam() {
		ServerHttpRequest request = requestWithQuery("token=");
		ServerHttpResponse response = Mockito.mock(ServerHttpResponse.class);
		Map<String, Object> attributes = new HashMap<>();

		boolean accepted = interceptor.beforeHandshake(request, response, wsHandler, attributes);

		assertThat(accepted).isFalse();
		Mockito.verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
		assertThat(attributes).doesNotContainKey(JwtHandshakeInterceptor.USER_ID_ATTRIBUTE);
	}

	@Test
	void rejectsHandshakeWithInvalidSignatureToken() {
		JwtService otherJwtService = new JwtService(OTHER_VALID_SECRET, TTL, ISSUER, AUDIENCE);
		String tokenSignedWithOtherKey = otherJwtService.generateToken(UUID.randomUUID());
		ServerHttpRequest request = requestWithQuery("token=" + tokenSignedWithOtherKey);
		ServerHttpResponse response = Mockito.mock(ServerHttpResponse.class);
		Map<String, Object> attributes = new HashMap<>();

		boolean accepted = interceptor.beforeHandshake(request, response, wsHandler, attributes);

		assertThat(accepted).isFalse();
		Mockito.verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
		assertThat(attributes).doesNotContainKey(JwtHandshakeInterceptor.USER_ID_ATTRIBUTE);
	}

	@Test
	void rejectsHandshakeWithMalformedToken() {
		ServerHttpRequest request = requestWithQuery("token=not-a-jwt-token");
		ServerHttpResponse response = Mockito.mock(ServerHttpResponse.class);
		Map<String, Object> attributes = new HashMap<>();

		boolean accepted = interceptor.beforeHandshake(request, response, wsHandler, attributes);

		assertThat(accepted).isFalse();
		Mockito.verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
		assertThat(attributes).doesNotContainKey(JwtHandshakeInterceptor.USER_ID_ATTRIBUTE);
	}

	@Test
	void acceptsHandshakeWithValidTokenAndStoresUserId() {
		UUID userId = UUID.randomUUID();
		String token = jwtService.generateToken(userId);
		ServerHttpRequest request = requestWithQuery("token=" + token);
		ServerHttpResponse response = Mockito.mock(ServerHttpResponse.class);
		Map<String, Object> attributes = new HashMap<>();

		boolean accepted = interceptor.beforeHandshake(request, response, wsHandler, attributes);

		assertThat(accepted).isTrue();
		Mockito.verifyNoInteractions(response);
		assertThat(attributes.get(JwtHandshakeInterceptor.USER_ID_ATTRIBUTE)).isEqualTo(userId);
	}

	private static ServerHttpRequest requestWithQuery(String queryString) {
		MockHttpServletRequest mockRequest = new MockHttpServletRequest("GET", "/ws");
		mockRequest.setQueryString(queryString);
		return new ServletServerHttpRequest(mockRequest);
	}
}
