package com.app.user;

import com.app.auth.EmailAlreadyExistsException;
import com.app.common.ResourceNotFoundException;
import com.app.security.RateLimitExceededException;
import com.app.security.RateLimitProperties;
import com.app.security.RateLimitService;
import com.app.security.SecurityAuditLogger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private SecurityAuditLogger securityAuditLogger;

	private UserProfileService userProfileService;

	@BeforeEach
	void setUp() {
		userProfileService = new UserProfileService(userRepository, new RateLimitService(), new RateLimitProperties(),
				securityAuditLogger);
	}

	private UserProfileService newServiceWithProfileUpdateLimit(int capacity, Duration window) {
		RateLimitProperties properties = new RateLimitProperties();
		properties.setProfileUpdate(new RateLimitProperties.Limit(capacity, window));
		return new UserProfileService(userRepository, new RateLimitService(), properties, securityAuditLogger);
	}

	private User existingUser(UUID userId) {
		User user = new User("Ana", "ana@example.com", "hash");
		ReflectionTestUtils.setField(user, "id", userId);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
		return user;
	}

	@Test
	void updatesOnlyTheName() {
		UUID userId = UUID.randomUUID();
		User user = existingUser(userId);

		UserProfileResponse response = userProfileService.updateProfile(userId, new UpdateProfileRequest("Ana Paula", null));

		assertThat(response.name()).isEqualTo("Ana Paula");
		assertThat(response.email()).isEqualTo("ana@example.com");
		assertThat(response.id()).isEqualTo(userId);
		assertThat(user.getEmail()).isEqualTo("ana@example.com");
		verify(userRepository, never()).existsByEmail(any());
		verify(securityAuditLogger).profileUpdated(userId, List.of("name"));
	}

	@Test
	void updatesOnlyTheEmail() {
		UUID userId = UUID.randomUUID();
		User user = existingUser(userId);
		when(userRepository.existsByEmail("nova@example.com")).thenReturn(false);

		UserProfileResponse response = userProfileService.updateProfile(userId,
				new UpdateProfileRequest(null, "nova@example.com"));

		assertThat(response.email()).isEqualTo("nova@example.com");
		assertThat(response.name()).isEqualTo("Ana");
		assertThat(user.getName()).isEqualTo("Ana");
	}

	@Test
	void updatesBothFieldsAtOnce() {
		UUID userId = UUID.randomUUID();
		existingUser(userId);
		when(userRepository.existsByEmail("nova@example.com")).thenReturn(false);

		UserProfileResponse response = userProfileService.updateProfile(userId,
				new UpdateProfileRequest("Ana Paula", "nova@example.com"));

		assertThat(response.name()).isEqualTo("Ana Paula");
		assertThat(response.email()).isEqualTo("nova@example.com");
		verify(securityAuditLogger).profileUpdated(userId, List.of("name", "email"));
	}

	@Test
	void emptyPayloadChangesNothingAndDoesNotSave() {
		UUID userId = UUID.randomUUID();
		User user = new User("Ana", "ana@example.com", "hash");
		ReflectionTestUtils.setField(user, "id", userId);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));

		UserProfileResponse response = userProfileService.updateProfile(userId, new UpdateProfileRequest(null, null));

		assertThat(response.name()).isEqualTo("Ana");
		assertThat(response.email()).isEqualTo("ana@example.com");
		verify(userRepository, never()).save(any());
	}

	@Test
	void resendingTheSameEmailIsNotAConflictWithItself() {
		UUID userId = UUID.randomUUID();
		User user = new User("Ana", "ana@example.com", "hash");
		ReflectionTestUtils.setField(user, "id", userId);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));

		UserProfileResponse response = userProfileService.updateProfile(userId,
				new UpdateProfileRequest(null, "ana@example.com"));

		assertThat(response.email()).isEqualTo("ana@example.com");
		verify(userRepository, never()).existsByEmail(any());
		verify(userRepository, never()).save(any());
		verifyNoInteractions(securityAuditLogger);
	}

	@Test
	void rejectsAnEmailAlreadyUsedByAnotherAccount() {
		UUID userId = UUID.randomUUID();
		User user = new User("Ana", "ana@example.com", "hash");
		ReflectionTestUtils.setField(user, "id", userId);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(userRepository.existsByEmail("ocupado@example.com")).thenReturn(true);

		assertThatThrownBy(() -> userProfileService.updateProfile(userId,
				new UpdateProfileRequest(null, "ocupado@example.com")))
			.isInstanceOf(EmailAlreadyExistsException.class);

		assertThat(user.getEmail()).isEqualTo("ana@example.com");
		verify(userRepository, never()).save(any());
		verifyNoInteractions(securityAuditLogger);
	}

	@Test
	void failsWhenTheAuthenticatedUserNoLongerExists() {
		UUID userId = UUID.randomUUID();
		when(userRepository.findById(userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userProfileService.updateProfile(userId, new UpdateProfileRequest("Ana", null)))
			.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void blocksTheUserAfterExceedingTheProfileUpdateRateLimit() {
		UUID userId = UUID.randomUUID();
		User user = new User("Ana", "ana@example.com", "hash");
		ReflectionTestUtils.setField(user, "id", userId);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
		UserProfileService service = newServiceWithProfileUpdateLimit(1, Duration.ofMinutes(5));

		service.updateProfile(userId, new UpdateProfileRequest("Ana Paula", null));

		assertThatThrownBy(() -> service.updateProfile(userId, new UpdateProfileRequest("Ana Clara", null)))
			.isInstanceOf(RateLimitExceededException.class);
	}

	@Test
	void theProfileUpdateBucketIsPerUser() {
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		User firstUser = new User("Ana", "ana@example.com", "hash");
		ReflectionTestUtils.setField(firstUser, "id", first);
		User secondUser = new User("Bia", "bia@example.com", "hash");
		ReflectionTestUtils.setField(secondUser, "id", second);
		when(userRepository.findById(first)).thenReturn(Optional.of(firstUser));
		when(userRepository.findById(second)).thenReturn(Optional.of(secondUser));
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
		UserProfileService service = newServiceWithProfileUpdateLimit(1, Duration.ofMinutes(5));

		service.updateProfile(first, new UpdateProfileRequest("Ana Paula", null));

		assertThat(service.updateProfile(second, new UpdateProfileRequest("Bia Helena", null)).name())
			.isEqualTo("Bia Helena");
	}
}
