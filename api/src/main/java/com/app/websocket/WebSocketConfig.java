package com.app.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Broker STOMP para eventos em tempo real (ex.: Match do Epico 5). Endpoint
 * {@code /ws} com fallback SockJS, autenticacao via {@link JwtHandshakeInterceptor}
 * no handshake, broker simples em {@code /topic} e prefixo de aplicacao {@code /app}.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	@Value("${app.cors.allowed-origin:http://localhost:5173}")
	private String allowedOrigin;

	private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

	public WebSocketConfig(JwtHandshakeInterceptor jwtHandshakeInterceptor) {
		this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws")
			.setAllowedOrigins(allowedOrigin)
			.addInterceptors(jwtHandshakeInterceptor)
			.withSockJS();
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/topic");
		registry.setApplicationDestinationPrefixes("/app");
	}
}
