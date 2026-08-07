package com.app.couple;

import com.app.security.RateLimitExceededException;
import com.app.security.RateLimitProperties;
import com.app.security.RateLimitService;
import com.app.security.SecurityAuditLogger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
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
		coupleService = new CoupleService(coupleRepository, inviteCodeGenerator, new RateLimitService(),
				new RateLimitProperties(), new CoupleProperties(), new SecurityAuditLogger());
	}

	private CoupleService newCoupleServiceWithJoinByUserLimit(int capacity, Duration window) {
		RateLimitProperties properties = new RateLimitProperties();
		properties.setCoupleJoinByUser(new RateLimitProperties.Limit(capacity, window));
		return new CoupleService(coupleRepository, inviteCodeGenerator, new RateLimitService(), properties,
				new CoupleProperties(), new SecurityAuditLogger());
	}

	@Test
	void createsCoupleForUserWithoutOne() {
		UUID userId = UUID.randomUUID();
		when(coupleRepository.findActiveByUserId(userId)).thenReturn(Optional.empty());
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
		when(coupleRepository.findActiveByUserId(userId)).thenReturn(Optional.empty());
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
		when(coupleRepository.findActiveByUserId(userId))
			.thenReturn(Optional.of(new Couple(userId, "EXIST01")));

		assertThatThrownBy(() -> coupleService.createCouple(userId))
			.isInstanceOf(UserAlreadyInCoupleException.class);

		verify(coupleRepository, never()).save(any());
	}

	@Test
	void returnsCurrentCoupleForUser() {
		UUID userId = UUID.randomUUID();
		Couple couple = new Couple(userId, "ABC234");
		when(coupleRepository.findActiveByUserId(userId)).thenReturn(Optional.of(couple));

		assertThat(coupleService.getCurrentCouple(userId)).contains(couple);
	}

	@Test
	void returnsEmptyWhenUserHasNoCouple() {
		UUID userId = UUID.randomUUID();
		when(coupleRepository.findActiveByUserId(userId)).thenReturn(Optional.empty());

		assertThat(coupleService.getCurrentCouple(userId)).isEmpty();
	}

	@Test
	void joinsCoupleWithValidInviteCode() {
		UUID user1Id = UUID.randomUUID();
		UUID user2Id = UUID.randomUUID();
		Couple couple = new Couple(user1Id, "ABC234");
		when(coupleRepository.findActiveByInviteCode("ABC234")).thenReturn(Optional.of(couple));
		when(coupleRepository.findActiveByUserId(user2Id)).thenReturn(Optional.empty());
		when(coupleRepository.save(any(Couple.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Couple joined = coupleService.joinCouple(user2Id, "ABC234");

		assertThat(joined.getUser2Id()).isEqualTo(user2Id);
	}

	@Test
	void rejectsJoinWithUnknownInviteCode() {
		UUID userId = UUID.randomUUID();
		when(coupleRepository.findActiveByInviteCode("NOPE")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> coupleService.joinCouple(userId, "NOPE"))
			.isInstanceOf(InviteCodeNotFoundException.class);
	}

	@Test
	void rejectsSelfJoin() {
		UUID userId = UUID.randomUUID();
		Couple couple = new Couple(userId, "ABC234");
		when(coupleRepository.findActiveByInviteCode("ABC234")).thenReturn(Optional.of(couple));

		assertThatThrownBy(() -> coupleService.joinCouple(userId, "ABC234"))
			.isInstanceOf(CannotJoinOwnCoupleException.class);

		verify(coupleRepository, never()).save(any());
	}

	@Test
	void rejectsJoinWhenUserAlreadyPaired() {
		UUID user1Id = UUID.randomUUID();
		UUID user2Id = UUID.randomUUID();
		Couple couple = new Couple(user1Id, "ABC234");
		when(coupleRepository.findActiveByInviteCode("ABC234")).thenReturn(Optional.of(couple));
		when(coupleRepository.findActiveByUserId(user2Id))
			.thenReturn(Optional.of(new Couple(user2Id, "OTHER01")));

		assertThatThrownBy(() -> coupleService.joinCouple(user2Id, "ABC234"))
			.isInstanceOf(UserAlreadyInCoupleException.class);

		verify(coupleRepository, never()).save(any());
	}

	@Test
	void blocksJoinAfterExceedingPerUserLimit() {
		CoupleService limitedCoupleService = newCoupleServiceWithJoinByUserLimit(1, Duration.ofMinutes(1));
		UUID userId = UUID.randomUUID();
		when(coupleRepository.findActiveByInviteCode("NOPE")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> limitedCoupleService.joinCouple(userId, "NOPE"))
			.isInstanceOf(InviteCodeNotFoundException.class);

		assertThatThrownBy(() -> limitedCoupleService.joinCouple(userId, "NOPE"))
			.isInstanceOf(RateLimitExceededException.class);

		verify(coupleRepository, never()).save(any());
	}

	@Test
	void releasesJoinByUserRateLimitAfterWindow() throws InterruptedException {
		CoupleService limitedCoupleService = newCoupleServiceWithJoinByUserLimit(1, Duration.ofMillis(150));
		UUID userId = UUID.randomUUID();
		when(coupleRepository.findActiveByInviteCode("NOPE")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> limitedCoupleService.joinCouple(userId, "NOPE"))
			.isInstanceOf(InviteCodeNotFoundException.class);
		assertThatThrownBy(() -> limitedCoupleService.joinCouple(userId, "NOPE"))
			.isInstanceOf(RateLimitExceededException.class);

		Thread.sleep(300);

		assertThatThrownBy(() -> limitedCoupleService.joinCouple(userId, "NOPE"))
			.isInstanceOf(InviteCodeNotFoundException.class);
	}

	@Test
	void rejectsJoinWithExpiredInviteCode() {
		UUID user1Id = UUID.randomUUID();
		UUID user2Id = UUID.randomUUID();
		Couple couple = new Couple(user1Id, "ABC234", Instant.now().minus(Duration.ofMinutes(1)));
		when(coupleRepository.findActiveByInviteCode("ABC234")).thenReturn(Optional.of(couple));

		assertThatThrownBy(() -> coupleService.joinCouple(user2Id, "ABC234"))
			.isInstanceOf(InviteCodeExpiredException.class);

		verify(coupleRepository, never()).save(any());
	}

	@Test
	void joinsCoupleWithValidUnexpiredInviteCode() {
		UUID user1Id = UUID.randomUUID();
		UUID user2Id = UUID.randomUUID();
		Couple couple = new Couple(user1Id, "ABC234", Instant.now().plus(Duration.ofDays(1)));
		when(coupleRepository.findActiveByInviteCode("ABC234")).thenReturn(Optional.of(couple));
		when(coupleRepository.findActiveByUserId(user2Id)).thenReturn(Optional.empty());
		when(coupleRepository.save(any(Couple.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Couple joined = coupleService.joinCouple(user2Id, "ABC234");

		assertThat(joined.getUser2Id()).isEqualTo(user2Id);
	}

	@Test
	void joinsCoupleWhenInviteCodeNeverExpires() {
		UUID user1Id = UUID.randomUUID();
		UUID user2Id = UUID.randomUUID();
		Couple couple = new Couple(user1Id, "ABC234");
		when(coupleRepository.findActiveByInviteCode("ABC234")).thenReturn(Optional.of(couple));
		when(coupleRepository.findActiveByUserId(user2Id)).thenReturn(Optional.empty());
		when(coupleRepository.save(any(Couple.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Couple joined = coupleService.joinCouple(user2Id, "ABC234");

		assertThat(joined.getUser2Id()).isEqualTo(user2Id);
	}

	@Test
	void successfulJoinInvalidatesInviteCodeAndItsExpiry() {
		UUID user1Id = UUID.randomUUID();
		UUID user2Id = UUID.randomUUID();
		Couple couple = new Couple(user1Id, "ABC234", Instant.now().plus(Duration.ofDays(1)));
		when(coupleRepository.findActiveByInviteCode("ABC234")).thenReturn(Optional.of(couple));
		when(coupleRepository.findActiveByUserId(user2Id)).thenReturn(Optional.empty());
		when(coupleRepository.save(any(Couple.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Couple joined = coupleService.joinCouple(user2Id, "ABC234");

		assertThat(joined.getInviteCode()).isNull();
		assertThat(joined.getInviteCodeExpiresAt()).isNull();
	}

	@Test
	void createCoupleGrantsInviteCodeExpiryFromConfiguredTtl() {
		UUID userId = UUID.randomUUID();
		when(coupleRepository.findActiveByUserId(userId)).thenReturn(Optional.empty());
		when(inviteCodeGenerator.generate()).thenReturn("ABC234");
		when(coupleRepository.existsByInviteCode("ABC234")).thenReturn(false);
		when(coupleRepository.save(any(Couple.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Instant before = Instant.now().plus(Duration.ofDays(7));
		Couple couple = coupleService.createCouple(userId);
		Instant after = Instant.now().plus(Duration.ofDays(7));

		assertThat(couple.getInviteCodeExpiresAt()).isBetween(before.minusSeconds(5), after.plusSeconds(5));
	}

	@Test
	void rejectsJoinWhenCoupleAlreadyFull() {
		UUID user1Id = UUID.randomUUID();
		UUID user2Id = UUID.randomUUID();
		UUID user3Id = UUID.randomUUID();
		Couple couple = new Couple(user1Id, "ABC234");
		couple.setUser2Id(user2Id);
		when(coupleRepository.findActiveByInviteCode("ABC234")).thenReturn(Optional.of(couple));
		when(coupleRepository.findActiveByUserId(user3Id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> coupleService.joinCouple(user3Id, "ABC234"))
			.isInstanceOf(CoupleAlreadyFullException.class);

		verify(coupleRepository, never()).save(any());
	}

	@Test
	void regeneratesInviteCodeForCreator() {
		UUID userId = UUID.randomUUID();
		Couple couple = new Couple(userId, "OLD0001", Instant.now().minus(Duration.ofMinutes(1)));
		when(coupleRepository.findActiveByUserId(userId)).thenReturn(Optional.of(couple));
		when(inviteCodeGenerator.generate()).thenReturn("NEW0001");
		when(coupleRepository.existsByInviteCode("NEW0001")).thenReturn(false);
		when(coupleRepository.save(any(Couple.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Instant before = Instant.now().plus(Duration.ofDays(7));
		Couple regenerated = coupleService.regenerateInviteCode(userId);
		Instant after = Instant.now().plus(Duration.ofDays(7));

		assertThat(regenerated.getInviteCode()).isEqualTo("NEW0001");
		assertThat(regenerated.getInviteCodeExpiresAt()).isBetween(before.minusSeconds(5), after.plusSeconds(5));
	}

	@Test
	void rejectsRegenerateWhenUserHasNoCouple() {
		UUID userId = UUID.randomUUID();
		when(coupleRepository.findActiveByUserId(userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> coupleService.regenerateInviteCode(userId))
			.isInstanceOf(CoupleNotFoundException.class);

		verify(coupleRepository, never()).save(any());
	}

	@Test
	void rejectsRegenerateWhenUserIsNotCreator() {
		UUID user1Id = UUID.randomUUID();
		UUID user2Id = UUID.randomUUID();
		Couple couple = new Couple(user1Id, "ABC234");
		when(coupleRepository.findActiveByUserId(user2Id)).thenReturn(Optional.of(couple));

		assertThatThrownBy(() -> coupleService.regenerateInviteCode(user2Id))
			.isInstanceOf(NotCoupleCreatorException.class);

		verify(coupleRepository, never()).save(any());
	}

	@Test
	void rejectsRegenerateWhenCoupleAlreadyPaired() {
		UUID user1Id = UUID.randomUUID();
		UUID user2Id = UUID.randomUUID();
		Couple couple = new Couple(user1Id, null);
		couple.setUser2Id(user2Id);
		when(coupleRepository.findActiveByUserId(user1Id)).thenReturn(Optional.of(couple));

		assertThatThrownBy(() -> coupleService.regenerateInviteCode(user1Id))
			.isInstanceOf(CoupleAlreadyFullException.class);

		verify(coupleRepository, never()).save(any());
	}
}
