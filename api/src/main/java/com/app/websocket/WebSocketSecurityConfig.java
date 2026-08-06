package com.app.websocket;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageAuthorizationContext;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

/**
 * Postura deny-by-default para mensagens de entrada do broker STOMP: so
 * SUBSCRIBE em {@code /topic/couple/{id}/**} com o id do proprio casal do
 * usuario da sessao (decidido por {@link CoupleDestinationAuthorizationManager})
 * e permitido. Qualquer SEND de cliente - direto para {@code /topic/**} ou
 * para {@code /app/**} - e negado; so o servidor publica nesses destinos via
 * {@code SimpMessagingTemplate}, que nao passa pelo clientInboundChannel e
 * portanto nao e afetado por esta regra. CONNECT/DISCONNECT/UNSUBSCRIBE e
 * afins continuam liberados para nao quebrar o handshake e o ciclo de vida
 * normal da sessao, ja autenticados por {@link JwtHandshakeInterceptor}.
 */
@Configuration
@EnableWebSocketSecurity
public class WebSocketSecurityConfig {

	@Bean
	AuthorizationManager<Message<?>> messageAuthorizationManager(
			CoupleDestinationAuthorizationManager coupleDestinationAuthorizationManager) {
		AuthorizationManager<MessageAuthorizationContext<?>> coupleTopicAuthorizationManager =
			(authentication, context) -> {
				SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(context.getMessage());
				String destination = accessor.getDestination();
				Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
				boolean authorized = coupleDestinationAuthorizationManager.isAuthorized(destination, sessionAttributes);
				return new AuthorizationDecision(authorized);
			};

		return MessageMatcherDelegatingAuthorizationManager.builder()
			.simpTypeMatchers(
				SimpMessageType.CONNECT,
				SimpMessageType.DISCONNECT,
				SimpMessageType.UNSUBSCRIBE,
				SimpMessageType.HEARTBEAT,
				SimpMessageType.OTHER).permitAll()
			.simpSubscribeDestMatchers("/topic/couple/{id}/**").access(coupleTopicAuthorizationManager)
			.anyMessage().denyAll()
			.build();
	}

	/**
	 * No-op: sobrescreve o {@code XorCsrfChannelInterceptor} default do
	 * {@code @EnableWebSocketSecurity}, que exigiria um token CSRF no frame
	 * CONNECT que o cliente atual nao envia. DECISAO E3 (Epico 4, migracao de
	 * autenticacao) - mantido deliberadamente no-op mesmo com o CSRF HTTP
	 * reativado na US-008: o handshake ja e autenticado por cookie
	 * ({@link JwtHandshakeInterceptor}), o SUBSCRIBE ja e autorizado por
	 * destino ({@link WebSocketSecurityConfig#messageAuthorizationManager}
	 * via {@link CoupleDestinationAuthorizationManager}), todo SEND de
	 * cliente e negado por {@code anyMessage().denyAll()} acima, e a CSP de
	 * producao aplica {@code frame-ancestors 'none'}. Nao reabrir esta
	 * decisao sem revisitar as quatro camadas acima.
	 */
	@Bean("csrfChannelInterceptor")
	ChannelInterceptor csrfChannelInterceptor() {
		return new ChannelInterceptor() {
		};
	}
}
