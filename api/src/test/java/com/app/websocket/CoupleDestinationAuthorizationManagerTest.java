package com.app.websocket;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import com.app.couple.Couple;
import com.app.couple.CoupleProperties;
import com.app.couple.CoupleRepository;
import com.app.couple.CoupleService;
import com.app.couple.InviteCodeGenerator;
import com.app.security.RateLimitProperties;
import com.app.security.RateLimitService;
import com.app.security.SecurityAuditLogger;

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

	/**
	 * E9.19 (epico 9, US-002): este componente nao ganhou nenhuma linha de codigo para tratar de casal
	 * dissolvido - ele herda o filtro porque {@code getCurrentCouple} resolve por
	 * {@code findActiveByUserId}. Por isso o teste monta um {@link CoupleService} REAL sobre um
	 * repositorio mockado: com a consulta que o servico usa de verdade nao devolvendo nada para o casal
	 * dissolvido (comportamento provado em {@code CoupleRepositoryTest}), o ex-membro nao consegue
	 * assinar o topico do casal que ele acabou de deixar - nem o dele, nem o do ex-parceiro.
	 */
	@Test
	void deniesExMemberSubscribingToTheTopicOfADissolvedCouple() {
		UUID userId = UUID.randomUUID();
		UUID dissolvedCoupleId = UUID.randomUUID();
		CoupleRepository coupleRepository = Mockito.mock(CoupleRepository.class);
		Mockito.when(coupleRepository.findActiveByUserId(userId)).thenReturn(Optional.empty());
		CoupleDestinationAuthorizationManager realManager = new CoupleDestinationAuthorizationManager(
			new CoupleService(coupleRepository, new InviteCodeGenerator(), new RateLimitService(),
					new RateLimitProperties(), new CoupleProperties(), new SecurityAuditLogger()));

		boolean authorized = realManager.isAuthorized(
			"/topic/couple/" + dissolvedCoupleId + "/notifications",
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
