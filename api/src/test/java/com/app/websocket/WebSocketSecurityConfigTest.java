package com.app.websocket;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import com.app.couple.Couple;
import com.app.couple.CoupleService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Cobre a autorizacao de mensagens de entrada do broker STOMP ligada pela
 * US-007: envia mensagens diretamente ao {@code clientInboundChannel} (o
 * mesmo canal em que {@code AuthorizationChannelInterceptor} e registrado por
 * {@code @EnableWebSocketSecurity}) e verifica que ele aceita ou rejeita
 * conforme a postura deny-by-default.
 */
@SpringBootTest
class WebSocketSecurityConfigTest {

	@Autowired
	@Qualifier("clientInboundChannel")
	private MessageChannel clientInboundChannel;

	@MockitoBean
	private CoupleService coupleService;

	@Test
	void allowsSubscribeToMatchTopicOfOwnCouple() {
		UUID userId = UUID.randomUUID();
		Couple couple = coupleOf(UUID.randomUUID());
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.of(couple));

		Message<byte[]> message = subscribeMessage(userId, "/topic/couple/" + couple.getId() + "/match");

		assertThat(clientInboundChannel.send(message)).isTrue();
	}

	@Test
	void allowsSubscribeToNotificationsTopicOfOwnCouple() {
		UUID userId = UUID.randomUUID();
		Couple couple = coupleOf(UUID.randomUUID());
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.of(couple));

		Message<byte[]> message = subscribeMessage(userId, "/topic/couple/" + couple.getId() + "/notifications");

		assertThat(clientInboundChannel.send(message)).isTrue();
	}

	@Test
	void deniesSubscribeToAnotherCouplesTopic() {
		UUID userId = UUID.randomUUID();
		Couple ownCouple = coupleOf(UUID.randomUUID());
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.of(ownCouple));

		Message<byte[]> message = subscribeMessage(userId, "/topic/couple/" + UUID.randomUUID() + "/match");

		assertThatThrownBy(() -> clientInboundChannel.send(message))
			.hasCauseInstanceOf(AccessDeniedException.class);
	}

	@Test
	void deniesSubscribeWithMalformedCoupleIdWithoutThrowing500() {
		UUID userId = UUID.randomUUID();
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.of(coupleOf(UUID.randomUUID())));

		Message<byte[]> message = subscribeMessage(userId, "/topic/couple/not-a-uuid/match");

		assertThatThrownBy(() -> clientInboundChannel.send(message))
			.hasCauseInstanceOf(AccessDeniedException.class);
	}

	@Test
	void deniesDirectClientSendToTopicDestination() {
		UUID userId = UUID.randomUUID();
		Couple couple = coupleOf(UUID.randomUUID());
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.of(couple));

		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
		accessor.setSessionId("session-send");
		accessor.setSessionAttributes(Map.of(JwtHandshakeInterceptor.USER_ID_ATTRIBUTE, userId));
		accessor.setDestination("/topic/couple/" + couple.getId() + "/match");
		Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		assertThatThrownBy(() -> clientInboundChannel.send(message))
			.hasCauseInstanceOf(AccessDeniedException.class);
	}

	@Test
	void deniesAnyMessageToAppPrefix() {
		UUID userId = UUID.randomUUID();
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.of(coupleOf(UUID.randomUUID())));

		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
		accessor.setSessionId("session-app");
		accessor.setSessionAttributes(Map.of(JwtHandshakeInterceptor.USER_ID_ATTRIBUTE, userId));
		accessor.setDestination("/app/match/like");
		Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		assertThatThrownBy(() -> clientInboundChannel.send(message))
			.hasCauseInstanceOf(AccessDeniedException.class);
	}

	@Test
	void allowsConnectFrame() {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
		accessor.setSessionId("session-connect");
		Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		assertThat(clientInboundChannel.send(message)).isTrue();
	}

	private Message<byte[]> subscribeMessage(UUID userId, String destination) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		accessor.setSessionId("session-" + userId);
		accessor.setSubscriptionId("sub-1");
		accessor.setSessionAttributes(Map.of(JwtHandshakeInterceptor.USER_ID_ATTRIBUTE, userId));
		accessor.setDestination(destination);
		return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
	}

	private static Couple coupleOf(UUID id) {
		Couple couple = new Couple(UUID.randomUUID(), "code-" + id);
		ReflectionTestUtils.setField(couple, "id", id);
		return couple;
	}
}
