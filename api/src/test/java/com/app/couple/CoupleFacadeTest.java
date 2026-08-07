package com.app.couple;

import com.app.common.ResourceNotFoundException;
import com.app.security.RateLimitProperties;
import com.app.security.RateLimitService;
import com.app.security.SecurityAuditLogger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Porta {@link CoupleFacade} (epico 9, US-002). O {@link CoupleService} aqui e REAL, so o repositorio e
 * mockado: o que estas provas precisam garantir e que a porta herda o filtro de casal dissolvido do
 * servico em vez de reimplementar a regra por fora.
 */
@ExtendWith(MockitoExtension.class)
class CoupleFacadeTest {

	@Mock
	private CoupleRepository coupleRepository;

	@Mock
	private InviteCodeGenerator inviteCodeGenerator;

	private CoupleFacade coupleFacade;

	@BeforeEach
	void setUp() {
		CoupleService coupleService = new CoupleService(coupleRepository, inviteCodeGenerator,
				new RateLimitService(), new RateLimitProperties(), new CoupleProperties(),
				new SecurityAuditLogger());
		coupleFacade = new CoupleFacade(coupleService);
	}

	@Test
	void findsTheCoupleIdOfAUserWithAnActiveCouple() {
		UUID userId = UUID.randomUUID();
		Couple couple = coupleWithId(userId, UUID.randomUUID());
		when(coupleRepository.findActiveByUserId(userId)).thenReturn(Optional.of(couple));

		assertThat(coupleFacade.findActiveCoupleId(userId)).contains(couple.getId());
	}

	@Test
	void findsNothingForAUserWithoutACouple() {
		UUID userId = UUID.randomUUID();
		when(coupleRepository.findActiveByUserId(userId)).thenReturn(Optional.empty());

		assertThat(coupleFacade.findActiveCoupleId(userId)).isEmpty();
	}

	/**
	 * Casal dissolvido: a consulta filtra por {@code dissolved_at is null} (provado em
	 * {@link CoupleRepositoryTest}), entao a porta enxerga exatamente o mesmo que um usuario sem casal.
	 */
	@Test
	void doesNotSeeADissolvedCouple() {
		UUID userId = UUID.randomUUID();
		when(coupleRepository.findActiveByUserId(userId)).thenReturn(Optional.empty());

		assertThat(coupleFacade.findActiveCoupleId(userId)).isEmpty();
		assertThatThrownBy(() -> coupleFacade.requireActiveCoupleId(userId))
			.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void requireReturnsTheCoupleIdWhenThereIsAnActiveCouple() {
		UUID userId = UUID.randomUUID();
		Couple couple = coupleWithId(userId, UUID.randomUUID());
		when(coupleRepository.findActiveByUserId(userId)).thenReturn(Optional.of(couple));

		assertThat(coupleFacade.requireActiveCoupleId(userId)).isEqualTo(couple.getId());
	}

	/** E9.16: preserva o 404 que tracking/match ja devolvem hoje para quem nao tem casal. */
	@Test
	void requireThrowsResourceNotFoundWhenThereIsNoActiveCouple() {
		UUID userId = UUID.randomUUID();
		when(coupleRepository.findActiveByUserId(userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> coupleFacade.requireActiveCoupleId(userId))
			.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void memberIdsReturnsBothMembersOfAPairedCouple() {
		UUID user1Id = UUID.randomUUID();
		UUID user2Id = UUID.randomUUID();
		Couple couple = coupleWithId(user1Id, user2Id);
		when(coupleRepository.findById(couple.getId())).thenReturn(Optional.of(couple));

		assertThat(coupleFacade.memberIds(couple.getId())).containsExactly(user1Id, user2Id);
	}

	/** Casal criado e ainda nao pareado: o user2Id nulo e PULADO, nao vira um null na lista. */
	@Test
	void memberIdsSkipsTheMissingPartner() {
		UUID user1Id = UUID.randomUUID();
		Couple couple = coupleWithId(user1Id, null);
		when(coupleRepository.findById(couple.getId())).thenReturn(Optional.of(couple));

		assertThat(coupleFacade.memberIds(couple.getId())).containsExactly(user1Id);
	}

	/** Estado impossivel pela FK: degrada a exibicao de nomes em vez de derrubar uma leitura. */
	@Test
	void memberIdsOfAnUnknownCoupleIsEmpty() {
		UUID coupleId = UUID.randomUUID();
		when(coupleRepository.findById(coupleId)).thenReturn(Optional.empty());

		assertThat(coupleFacade.memberIds(coupleId)).isEmpty();
	}

	@Test
	void dissolveIfActiveDissolvesTheActiveCouple() {
		UUID userId = UUID.randomUUID();
		Couple couple = coupleWithId(userId, UUID.randomUUID());
		when(coupleRepository.findActiveByUserId(userId)).thenReturn(Optional.of(couple));
		when(coupleRepository.save(any(Couple.class))).thenAnswer(invocation -> invocation.getArgument(0));

		coupleFacade.dissolveIfActive(userId);

		assertThat(couple.isActive()).isFalse();
		assertThat(couple.getDissolvedAt()).isNotNull();
		verify(coupleRepository).save(couple);
	}

	/** No-op sem excecao: a US-007 exclui a conta de quem ja esta sem casal por este mesmo caminho. */
	@Test
	void dissolveIfActiveIsANoOpForAUserWithoutACouple() {
		UUID userId = UUID.randomUUID();
		when(coupleRepository.findActiveByUserId(userId)).thenReturn(Optional.empty());

		coupleFacade.dissolveIfActive(userId);

		verify(coupleRepository, never()).save(any(Couple.class));
	}

	private Couple coupleWithId(UUID user1Id, UUID user2Id) {
		Couple couple = new Couple(user1Id, "ABC12345");
		couple.setUser2Id(user2Id);
		ReflectionTestUtils.setField(couple, "id", UUID.randomUUID());
		return couple;
	}
}
