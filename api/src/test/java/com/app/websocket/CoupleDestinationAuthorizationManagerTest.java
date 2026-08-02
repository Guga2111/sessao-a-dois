package com.app.websocket;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import com.app.couple.Couple;
import com.app.couple.CoupleService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre a decisao de autorizacao de destino STOMP do casal: so o proprio
 * casal do usuario da sessao pode assinar {@code /topic/couple/{id}/**}.
 */
class CoupleDestinationAuthorizationManagerTest {

	private final CoupleService coupleService = Mockito.mock(CoupleService.class);
	private final CoupleDestinationAuthorizationManager manager =
		new CoupleDestinationAuthorizationManager(coupleService);

	@Test
	void authorizesMatchTopicOfOwnCouple() {
		UUID userId = UUID.randomUUID();
		Couple couple = coupleOf(UUID.randomUUID());
		Mockito.when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.of(couple));

		boolean authorized = manager.isAuthorized(
			"/topic/couple/" + couple.getId() + "/match",
			sessionAttributes(userId));

		assertThat(authorized).isTrue();
	}

	@Test
	void authorizesNotificationsTopicOfOwnCouple() {
		UUID userId = UUID.randomUUID();
		Couple couple = coupleOf(UUID.randomUUID());
		Mockito.when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.of(couple));

		boolean authorized = manager.isAuthorized(
			"/topic/couple/" + couple.getId() + "/notifications",
			sessionAttributes(userId));

		assertThat(authorized).isTrue();
	}

	@Test
	void deniesDestinationOfAnotherCouple() {
		UUID userId = UUID.randomUUID();
		Couple ownCouple = coupleOf(UUID.randomUUID());
		UUID otherCoupleId = UUID.randomUUID();
		Mockito.when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.of(ownCouple));

		boolean authorized = manager.isAuthorized(
			"/topic/couple/" + otherCoupleId + "/match",
			sessionAttributes(userId));

		assertThat(authorized).isFalse();
	}

	@Test
	void deniesMalformedCoupleIdWithoutThrowing() {
		UUID userId = UUID.randomUUID();
		Mockito.when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.of(coupleOf(UUID.randomUUID())));

		boolean authorized = manager.isAuthorized(
			"/topic/couple/not-a-uuid/match",
			sessionAttributes(userId));

		assertThat(authorized).isFalse();
	}

	@Test
	void deniesWhenUserHasNoCouple() {
		UUID userId = UUID.randomUUID();
		Mockito.when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.empty());

		boolean authorized = manager.isAuthorized(
			"/topic/couple/" + UUID.randomUUID() + "/match",
			sessionAttributes(userId));

		assertThat(authorized).isFalse();
	}

	@Test
	void deniesWhenSessionHasNoUserId() {
		boolean authorized = manager.isAuthorized(
			"/topic/couple/" + UUID.randomUUID() + "/match",
			Map.of());

		assertThat(authorized).isFalse();
		Mockito.verifyNoInteractions(coupleService);
	}

	private static Couple coupleOf(UUID id) {
		Couple couple = new Couple(UUID.randomUUID(), "code-" + id);
		ReflectionTestUtils.setField(couple, "id", id);
		return couple;
	}

	private static Map<String, Object> sessionAttributes(UUID userId) {
		return Map.of(JwtHandshakeInterceptor.USER_ID_ATTRIBUTE, userId);
	}
}
