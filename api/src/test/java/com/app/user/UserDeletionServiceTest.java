package com.app.user;

import com.app.auth.InvalidCredentialsException;
import com.app.auth.RefreshTokenService;
import com.app.common.ResourceNotFoundException;
import com.app.couple.CoupleFacade;
import com.app.match.MatchFacade;
import com.app.notification.NotificationFacade;
import com.app.security.RateLimitExceededException;
import com.app.security.RateLimitProperties;
import com.app.security.RateLimitService;
import com.app.security.SecurityAuditLogger;
import com.app.tracking.TrackingFacade;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDeletionServiceTest {

	private static final String SENHA = "senha-atual-123";

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private CoupleFacade coupleFacade;

	@Mock
	private TrackingFacade trackingFacade;

	@Mock
	private RefreshTokenService refreshTokenService;

	@Mock
	private MatchFacade matchFacade;

	@Mock
	private NotificationFacade notificationFacade;

	@Mock
	private SecurityAuditLogger securityAuditLogger;

	private UserDeletionService userDeletionService;

	@BeforeEach
	void setUp() {
		userDeletionService = newService(new RateLimitProperties());
	}

	private UserDeletionService newService(RateLimitProperties properties) {
		return new UserDeletionService(userRepository, passwordEncoder, coupleFacade, trackingFacade,
				refreshTokenService, matchFacade, notificationFacade, new RateLimitService(), properties,
				securityAuditLogger);
	}

	private User existingUser(UUID userId) {
		User user = new User("Ana", "ana@example.com", "hash");
		ReflectionTestUtils.setField(user, "id", userId);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		return user;
	}

	/**
	 * A ordem nao e cosmetica: {@code user_review} e {@code refresh_token} tem FK para
	 * {@code users} e precisam sair antes do {@code delete} final.
	 */
	@Test
	void deletesEveryFeaturesDataThroughItsPortAndThenTheAccount() {
		UUID userId = UUID.randomUUID();
		User user = existingUser(userId);
		when(passwordEncoder.matches(SENHA, user.getPasswordHash())).thenReturn(true);

		userDeletionService.deleteAccount(userId, new DeleteAccountRequest(SENHA));

		InOrder ordered = inOrder(coupleFacade, trackingFacade, refreshTokenService, matchFacade, notificationFacade,
				userRepository);
		ordered.verify(coupleFacade).dissolveIfActive(userId);
		ordered.verify(trackingFacade).deleteUserData(userId);
		ordered.verify(refreshTokenService).deleteAllForUser(userId);
		ordered.verify(matchFacade).deleteUserData(userId);
		ordered.verify(notificationFacade).deleteUserData(userId);
		ordered.verify(userRepository).delete(user);
		verify(securityAuditLogger).accountDeleted(userId);
	}

	/** Sem casal ativo a porta e no-op (E9.7 nunca e alcancada) e o resto acontece igual. */
	@Test
	void deletesTheAccountOfSomeoneWithoutACouple() {
		UUID userId = UUID.randomUUID();
		User user = existingUser(userId);
		when(passwordEncoder.matches(SENHA, user.getPasswordHash())).thenReturn(true);

		userDeletionService.deleteAccount(userId, new DeleteAccountRequest(SENHA));

		verify(coupleFacade).dissolveIfActive(userId);
		verify(userRepository).delete(user);
	}

	@Test
	void wrongPasswordDeletesNothing() {
		UUID userId = UUID.randomUUID();
		User user = existingUser(userId);
		when(passwordEncoder.matches("errada", user.getPasswordHash())).thenReturn(false);

		assertThatThrownBy(() -> userDeletionService.deleteAccount(userId, new DeleteAccountRequest("errada")))
			.isInstanceOf(InvalidCredentialsException.class);

		verifyNoInteractions(coupleFacade, trackingFacade, refreshTokenService, matchFacade, notificationFacade,
				securityAuditLogger);
		verify(userRepository, never()).delete(user);
	}

	@Test
	void unknownUserIsNotFound() {
		UUID userId = UUID.randomUUID();
		when(userRepository.findById(userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userDeletionService.deleteAccount(userId, new DeleteAccountRequest(SENHA)))
			.isInstanceOf(ResourceNotFoundException.class);

		verifyNoInteractions(coupleFacade, trackingFacade, refreshTokenService, matchFacade, notificationFacade);
	}

	@Test
	void blocksTheUserAfterTheHourlyLimit() {
		RateLimitProperties properties = new RateLimitProperties();
		properties.setAccountDelete(new RateLimitProperties.Limit(1, Duration.ofMinutes(10)));
		UserDeletionService service = newService(properties);
		UUID userId = UUID.randomUUID();
		User user = existingUser(userId);
		when(passwordEncoder.matches(SENHA, user.getPasswordHash())).thenReturn(true);

		service.deleteAccount(userId, new DeleteAccountRequest(SENHA));

		assertThatThrownBy(() -> service.deleteAccount(userId, new DeleteAccountRequest(SENHA)))
			.isInstanceOf(RateLimitExceededException.class);
	}

	/** O balde e por usuario: estourar o de um nao pode barrar o outro. */
	@Test
	void theBucketIsPerUser() {
		RateLimitProperties properties = new RateLimitProperties();
		properties.setAccountDelete(new RateLimitProperties.Limit(1, Duration.ofMinutes(10)));
		UserDeletionService service = newService(properties);
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		User firstUser = existingUser(first);
		User secondUser = existingUser(second);
		when(passwordEncoder.matches(SENHA, firstUser.getPasswordHash())).thenReturn(true);

		service.deleteAccount(first, new DeleteAccountRequest(SENHA));
		service.deleteAccount(second, new DeleteAccountRequest(SENHA));

		verify(userRepository).delete(firstUser);
		verify(userRepository).delete(secondUser);
	}
}
