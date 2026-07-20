package com.app.couple;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoupleServiceTest {

	@Mock
	private CoupleRepository coupleRepository;

	@Mock
	private InviteCodeGenerator inviteCodeGenerator;

	private CoupleService coupleService;

	@BeforeEach
	void setUp() {
		coupleService = new CoupleService(coupleRepository, inviteCodeGenerator);
	}

	@Test
	void createsCoupleForUserWithoutOne() {
		UUID userId = UUID.randomUUID();
		when(coupleRepository.findByUser1IdOrUser2Id(userId, userId)).thenReturn(Optional.empty());
		when(inviteCodeGenerator.generate()).thenReturn("ABC234");
		when(coupleRepository.existsByInviteCode("ABC234")).thenReturn(false);
		when(coupleRepository.save(any(Couple.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Couple couple = coupleService.createCouple(userId);

		assertThat(couple.getUser1Id()).isEqualTo(userId);
		assertThat(couple.getInviteCode()).isEqualTo("ABC234");
		assertThat(couple.getUser2Id()).isNull();
	}

	@Test
	void regeneratesInviteCodeOnCollision() {
		UUID userId = UUID.randomUUID();
		when(coupleRepository.findByUser1IdOrUser2Id(userId, userId)).thenReturn(Optional.empty());
		when(inviteCodeGenerator.generate()).thenReturn("DUP0001", "UNIQ001");
		when(coupleRepository.existsByInviteCode("DUP0001")).thenReturn(true);
		when(coupleRepository.existsByInviteCode("UNIQ001")).thenReturn(false);
		when(coupleRepository.save(any(Couple.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Couple couple = coupleService.createCouple(userId);

		assertThat(couple.getInviteCode()).isEqualTo("UNIQ001");
	}

	@Test
	void rejectsCreationWhenUserAlreadyInCouple() {
		UUID userId = UUID.randomUUID();
		when(coupleRepository.findByUser1IdOrUser2Id(userId, userId))
			.thenReturn(Optional.of(new Couple(userId, "EXIST01")));

		assertThatThrownBy(() -> coupleService.createCouple(userId))
			.isInstanceOf(UserAlreadyInCoupleException.class);

		verify(coupleRepository, never()).save(any());
	}

	@Test
	void returnsCurrentCoupleForUser() {
		UUID userId = UUID.randomUUID();
		Couple couple = new Couple(userId, "ABC234");
		when(coupleRepository.findByUser1IdOrUser2Id(userId, userId)).thenReturn(Optional.of(couple));

		assertThat(coupleService.getCurrentCouple(userId)).contains(couple);
	}
}
