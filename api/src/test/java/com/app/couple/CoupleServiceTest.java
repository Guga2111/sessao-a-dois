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

	@Test
	void joinsCoupleWithValidInviteCode() {
		UUID user1Id = UUID.randomUUID();
		UUID user2Id = UUID.randomUUID();
		Couple couple = new Couple(user1Id, "ABC234");
		when(coupleRepository.findByInviteCode("ABC234")).thenReturn(Optional.of(couple));
		when(coupleRepository.findByUser1IdOrUser2Id(user2Id, user2Id)).thenReturn(Optional.empty());
		when(coupleRepository.save(any(Couple.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Couple joined = coupleService.joinCouple(user2Id, "ABC234");

		assertThat(joined.getUser2Id()).isEqualTo(user2Id);
	}

	@Test
	void rejectsJoinWithUnknownInviteCode() {
		UUID userId = UUID.randomUUID();
		when(coupleRepository.findByInviteCode("NOPE")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> coupleService.joinCouple(userId, "NOPE"))
			.isInstanceOf(InviteCodeNotFoundException.class);
	}

	@Test
	void rejectsSelfJoin() {
		UUID userId = UUID.randomUUID();
		Couple couple = new Couple(userId, "ABC234");
		when(coupleRepository.findByInviteCode("ABC234")).thenReturn(Optional.of(couple));

		assertThatThrownBy(() -> coupleService.joinCouple(userId, "ABC234"))
			.isInstanceOf(CannotJoinOwnCoupleException.class);

		verify(coupleRepository, never()).save(any());
	}

	@Test
	void rejectsJoinWhenUserAlreadyPaired() {
		UUID user1Id = UUID.randomUUID();
		UUID user2Id = UUID.randomUUID();
		Couple couple = new Couple(user1Id, "ABC234");
		when(coupleRepository.findByInviteCode("ABC234")).thenReturn(Optional.of(couple));
		when(coupleRepository.findByUser1IdOrUser2Id(user2Id, user2Id))
			.thenReturn(Optional.of(new Couple(user2Id, "OTHER01")));

		assertThatThrownBy(() -> coupleService.joinCouple(user2Id, "ABC234"))
			.isInstanceOf(UserAlreadyInCoupleException.class);

		verify(coupleRepository, never()).save(any());
	}

	@Test
	void rejectsJoinWhenCoupleAlreadyFull() {
		UUID user1Id = UUID.randomUUID();
		UUID user2Id = UUID.randomUUID();
		UUID user3Id = UUID.randomUUID();
		Couple couple = new Couple(user1Id, "ABC234");
		couple.setUser2Id(user2Id);
		when(coupleRepository.findByInviteCode("ABC234")).thenReturn(Optional.of(couple));
		when(coupleRepository.findByUser1IdOrUser2Id(user3Id, user3Id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> coupleService.joinCouple(user3Id, "ABC234"))
			.isInstanceOf(CoupleAlreadyFullException.class);

		verify(coupleRepository, never()).save(any());
	}
}
