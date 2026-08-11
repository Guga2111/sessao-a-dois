package com.app.auth;

import com.app.security.JwtService;
import com.app.security.RateLimitExceededException;
import com.app.security.RateLimitProperties;
import com.app.security.RateLimitService;
import com.app.security.SecurityAuditLogger;
import com.app.user.User;
import com.app.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtService jwtService;

	@Mock
	private RefreshTokenService refreshTokenService;

	@Mock
	private PasswordResetDispatcher passwordResetDispatcher;

	@Mock
	private PasswordResetService passwordResetService;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	private RateLimitProperties rateLimitProperties;

	private AuthService authService;

	@BeforeEach
	void setUp() {
		rateLimitProperties = new RateLimitProperties();
		authService = new AuthService(userRepository, passwordEncoder, jwtService, refreshTokenService,
				new RateLimitService(), rateLimitProperties, new SecurityAuditLogger(), passwordResetDispatcher,
				passwordResetService, eventPublisher);
	}

	private static AuthService newAuthServiceWithLoginByEmailLimit(UserRepository userRepository,
			PasswordEncoder passwordEncoder, JwtService jwtService, RefreshTokenService refreshTokenService,
			int capacity, Duration window) {
		RateLimitProperties properties = new RateLimitProperties();
		properties.setLoginByEmail(new RateLimitProperties.Limit(capacity, window));
		return new AuthService(userRepository, passwordEncoder, jwtService, refreshTokenService,
				new RateLimitService(), properties, new SecurityAuditLogger(), Mockito.mock(PasswordResetDispatcher.class),
				Mockito.mock(PasswordResetService.class), Mockito.mock(ApplicationEventPublisher.class));
	}

	private static AuthService newAuthServiceWithForgotPasswordByEmailLimit(UserRepository userRepository,
			PasswordResetDispatcher passwordResetDispatcher, int capacity, Duration window) {
		RateLimitProperties properties = new RateLimitProperties();
		properties.setForgotPasswordByEmail(new RateLimitProperties.Limit(capacity, window));
		return new AuthService(userRepository, Mockito.mock(PasswordEncoder.class), Mockito.mock(JwtService.class),
				Mockito.mock(RefreshTokenService.class), new RateLimitService(), properties, new SecurityAuditLogger(),
				passwordResetDispatcher, Mockito.mock(PasswordResetService.class),
				Mockito.mock(ApplicationEventPublisher.class));
	}

	@Test
	void registersUserWithHashedPassword() {
		var request = new RegisterRequest("Ana", "ana@example.com", "senha1234");

		when(userRepository.existsByEmail("ana@example.com")).thenReturn(false);
		when(passwordEncoder.encode("senha1234")).thenReturn("hashed-password");
		when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		User saved = authService.register(request);

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(captor.capture());

		assertThat(captor.getValue().getEmail()).isEqualTo("ana@example.com");
		assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed-password");
		assertThat(saved.getName()).isEqualTo("Ana");
	}

	@Test
	void rejectsDuplicateEmail() {
		var request = new RegisterRequest("Ana", "ana@example.com", "senha1234");

		when(userRepository.existsByEmail("ana@example.com")).thenReturn(true);

		assertThatThrownBy(() -> authService.register(request))
			.isInstanceOf(EmailAlreadyExistsException.class);

		verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
		verify(passwordEncoder, never()).encode(anyString());
	}

	@Test
	void logsInSuccessfullyAndReturnsTokens() {
		var request = new LoginRequest("ana@example.com", "senha1234");
		User user = new User("Ana", "ana@example.com", "hashed-password");

		when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("senha1234", "hashed-password")).thenReturn(true);
		when(jwtService.generateToken(any())).thenReturn("jwt-token");
		when(refreshTokenService.issue(any(), any(), any())).thenReturn("refresh-token");

		AuthService.LoginResult result = authService.login(request, "Mozilla/5.0", "203.0.113.1");

		assertThat(result.accessToken()).isEqualTo("jwt-token");
		assertThat(result.refreshToken()).isEqualTo("refresh-token");
		assertThat(result.user()).isEqualTo(user);
		verify(refreshTokenService).issue(user.getId(), "Mozilla/5.0", "203.0.113.1");
	}

	@Test
	void rejectsLoginWithWrongPassword() {
		var request = new LoginRequest("ana@example.com", "wrong-password");
		User user = new User("Ana", "ana@example.com", "hashed-password");

		when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

		assertThatThrownBy(() -> authService.login(request, "Mozilla/5.0", "203.0.113.1"))
			.isInstanceOf(InvalidCredentialsException.class);
	}

	@Test
	void rejectsLoginWithUnknownEmail() {
		var request = new LoginRequest("desconhecido@example.com", "senha1234");

		when(userRepository.findByEmail("desconhecido@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login(request, "Mozilla/5.0", "203.0.113.1"))
			.isInstanceOf(InvalidCredentialsException.class);

		verify(passwordEncoder).matches(eq("senha1234"), anyString());
	}

	@Test
	void unknownEmailLoginInvokesPasswordEncoderAgainstDummyHash() {
		var request = new LoginRequest("desconhecido2@example.com", "qualquer-senha");

		when(userRepository.findByEmail("desconhecido2@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login(request, "Mozilla/5.0", "203.0.113.1"))
			.isInstanceOf(InvalidCredentialsException.class);

		ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
		verify(passwordEncoder).matches(eq("qualquer-senha"), hashCaptor.capture());
		assertThat(hashCaptor.getValue()).isNotNull().startsWith("$2a$");
	}

	@Test
	void blocksLoginByEmailAfterExceedingLimitEvenWhenEmailDoesNotExist() {
		AuthService limitedAuthService = newAuthServiceWithLoginByEmailLimit(userRepository, passwordEncoder,
				jwtService, refreshTokenService, 1, Duration.ofMinutes(1));
		var request = new LoginRequest("nao-existe@example.com", "senha1234");
		when(userRepository.findByEmail("nao-existe@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> limitedAuthService.login(request, "Mozilla/5.0", "203.0.113.1"))
			.isInstanceOf(InvalidCredentialsException.class);

		assertThatThrownBy(() -> limitedAuthService.login(request, "Mozilla/5.0", "203.0.113.1"))
			.isInstanceOf(RateLimitExceededException.class);

		verify(passwordEncoder, org.mockito.Mockito.times(1)).matches(anyString(), anyString());
	}

	@Test
	void loginByEmailRateLimitIgnoresCaseAndSurroundingSpaces() {
		AuthService limitedAuthService = newAuthServiceWithLoginByEmailLimit(userRepository, passwordEncoder,
				jwtService, refreshTokenService, 1, Duration.ofMinutes(1));
		when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

		assertThatThrownBy(
				() -> limitedAuthService.login(new LoginRequest("ana@example.com", "x"), "Mozilla/5.0", "203.0.113.1"))
			.isInstanceOf(InvalidCredentialsException.class);

		assertThatThrownBy(() -> limitedAuthService.login(new LoginRequest(" ANA@Example.com ", "x"), "Mozilla/5.0",
				"203.0.113.1"))
			.isInstanceOf(RateLimitExceededException.class);
	}

	@Test
	void releasesLoginByEmailRateLimitAfterWindow() throws InterruptedException {
		AuthService limitedAuthService = newAuthServiceWithLoginByEmailLimit(userRepository, passwordEncoder,
				jwtService, refreshTokenService, 1, Duration.ofMillis(150));
		var request = new LoginRequest("nao-existe2@example.com", "senha1234");
		when(userRepository.findByEmail("nao-existe2@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> limitedAuthService.login(request, "Mozilla/5.0", "203.0.113.1"))
			.isInstanceOf(InvalidCredentialsException.class);
		assertThatThrownBy(() -> limitedAuthService.login(request, "Mozilla/5.0", "203.0.113.1"))
			.isInstanceOf(RateLimitExceededException.class);

		Thread.sleep(300);

		assertThatThrownBy(() -> limitedAuthService.login(request, "Mozilla/5.0", "203.0.113.1"))
			.isInstanceOf(InvalidCredentialsException.class);
	}

	@Test
	void refreshRotatesTokenAndIssuesNewAccessTokenForRotatedUser() {
		UUID userId = UUID.randomUUID();
		when(refreshTokenService.rotate("raw-refresh-token", "Mozilla/5.0", "203.0.113.1"))
			.thenReturn(new RefreshTokenService.RotationResult(userId, "new-refresh-token"));
		when(jwtService.generateToken(userId)).thenReturn("new-access-token");

		AuthService.RefreshResult result = authService.refresh("raw-refresh-token", "Mozilla/5.0", "203.0.113.1");

		assertThat(result.accessToken()).isEqualTo("new-access-token");
		assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
	}

	@Test
	void logoutDelegatesToRefreshTokenServiceRevoke() {
		authService.logout("raw-refresh-token");

		verify(refreshTokenService).revoke("raw-refresh-token");
	}

	@Test
	void changePasswordSavesTheNewHashAndDropsEverySession() {
		UUID userId = UUID.randomUUID();
		User user = new User("Ana", "ana@example.com", "hash-antigo");
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("senha-atual", "hash-antigo")).thenReturn(true);
		when(passwordEncoder.encode("senha-nova-1234")).thenReturn("hash-novo");

		authService.changePassword(userId, new ChangePasswordRequest("senha-atual", "senha-nova-1234"));

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(captor.capture());
		assertThat(captor.getValue().getPasswordHash()).isEqualTo("hash-novo");
		verify(refreshTokenService).revokeFamily(userId);

		ArgumentCaptor<PasswordChangedNoticeEvent> eventCaptor = ArgumentCaptor.forClass(PasswordChangedNoticeEvent.class);
		verify(eventPublisher).publishEvent(eventCaptor.capture());
		assertThat(eventCaptor.getValue().userId()).isEqualTo(userId);
		assertThat(eventCaptor.getValue().email()).isEqualTo("ana@example.com");
	}

	@Test
	void changePasswordWithWrongCurrentPasswordChangesNothing() {
		UUID userId = UUID.randomUUID();
		User user = new User("Ana", "ana@example.com", "hash-antigo");
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("senha-errada", "hash-antigo")).thenReturn(false);

		assertThatThrownBy(
				() -> authService.changePassword(userId, new ChangePasswordRequest("senha-errada", "senha-nova-1234")))
			.isInstanceOf(InvalidCredentialsException.class);

		assertThat(user.getPasswordHash()).isEqualTo("hash-antigo");
		verify(userRepository, never()).save(any(User.class));
		verify(refreshTokenService, never()).revokeFamily(any());
		verify(passwordEncoder, never()).encode(anyString());
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void blocksPasswordChangeAfterExceedingThePerUserLimit() {
		RateLimitProperties properties = new RateLimitProperties();
		properties.setPasswordChange(new RateLimitProperties.Limit(1, Duration.ofMinutes(1)));
		AuthService limitedAuthService = new AuthService(userRepository, passwordEncoder, jwtService,
				refreshTokenService, new RateLimitService(), properties, new SecurityAuditLogger(), passwordResetDispatcher,
				passwordResetService, eventPublisher);
		UUID userId = UUID.randomUUID();
		User user = new User("Ana", "ana@example.com", "hash-antigo");
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("senha-atual", "hash-antigo")).thenReturn(true);
		when(passwordEncoder.encode("senha-nova-1234")).thenReturn("hash-novo");

		limitedAuthService.changePassword(userId, new ChangePasswordRequest("senha-atual", "senha-nova-1234"));

		assertThatThrownBy(() -> limitedAuthService.changePassword(userId,
				new ChangePasswordRequest("senha-atual", "senha-nova-1234")))
			.isInstanceOf(RateLimitExceededException.class);

		verify(userRepository, org.mockito.Mockito.times(1)).save(any(User.class));
	}

	@Test
	void passwordChangeRateLimitIsScopedPerUser() {
		RateLimitProperties properties = new RateLimitProperties();
		properties.setPasswordChange(new RateLimitProperties.Limit(1, Duration.ofMinutes(1)));
		AuthService limitedAuthService = new AuthService(userRepository, passwordEncoder, jwtService,
				refreshTokenService, new RateLimitService(), properties, new SecurityAuditLogger(), passwordResetDispatcher,
				passwordResetService, eventPublisher);
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		// Uma instancia por chamada: a primeira troca muda o hash da entidade em memoria, e
		// reusar o mesmo objeto faria a segunda cair em "senha atual errada" por acidente.
		when(userRepository.findById(any(UUID.class)))
			.thenAnswer(invocation -> Optional.of(new User("Ana", "ana@example.com", "hash-antigo")));
		when(passwordEncoder.matches("senha-atual", "hash-antigo")).thenReturn(true);
		when(passwordEncoder.encode("senha-nova-1234")).thenReturn("hash-novo");

		limitedAuthService.changePassword(first, new ChangePasswordRequest("senha-atual", "senha-nova-1234"));
		limitedAuthService.changePassword(second, new ChangePasswordRequest("senha-atual", "senha-nova-1234"));

		verify(refreshTokenService).revokeFamily(first);
		verify(refreshTokenService).revokeFamily(second);
	}

	@Test
	void refreshPropagatesRotationFailure() {
		when(refreshTokenService.rotate(anyString(), any(), any())).thenThrow(new RefreshReuseDetectedException());

		assertThatThrownBy(() -> authService.refresh("reused-token", "Mozilla/5.0", "203.0.113.1"))
			.isInstanceOf(RefreshReuseDetectedException.class);

		verify(jwtService, never()).generateToken(any());
	}

	@Test
	void forgotPasswordDispatchesForAnExistingEmail() {
		User user = new User("Ana", "ana@example.com", "hash");
		when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));

		authService.forgotPassword("ana@example.com");

		verify(passwordResetDispatcher).dispatch(user);
	}

	@Test
	void forgotPasswordDoesNothingExtraForAnUnknownEmail() {
		when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

		authService.forgotPassword("ghost@example.com");

		verify(passwordResetDispatcher, never()).dispatch(any());
	}

	@Test
	void blocksForgotPasswordByEmailAfterExceedingLimitEvenWhenEmailDoesNotExist() {
		AuthService limitedAuthService = newAuthServiceWithForgotPasswordByEmailLimit(userRepository,
				passwordResetDispatcher, 1, Duration.ofMinutes(1));
		when(userRepository.findByEmail("nao-existe@example.com")).thenReturn(Optional.empty());

		limitedAuthService.forgotPassword("nao-existe@example.com");

		assertThatThrownBy(() -> limitedAuthService.forgotPassword("nao-existe@example.com"))
			.isInstanceOf(RateLimitExceededException.class);

		verify(passwordResetDispatcher, never()).dispatch(any());
	}

	@Test
	void forgotPasswordByEmailRateLimitIgnoresCaseAndSurroundingSpaces() {
		AuthService limitedAuthService = newAuthServiceWithForgotPasswordByEmailLimit(userRepository,
				passwordResetDispatcher, 1, Duration.ofMinutes(1));
		when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

		limitedAuthService.forgotPassword("ana@example.com");

		assertThatThrownBy(() -> limitedAuthService.forgotPassword(" ANA@Example.com "))
			.isInstanceOf(RateLimitExceededException.class);
	}

	@Test
	void forgotPasswordByEmailRateLimitAlsoAppliesWhenEmailExists() {
		AuthService limitedAuthService = newAuthServiceWithForgotPasswordByEmailLimit(userRepository,
				passwordResetDispatcher, 1, Duration.ofMinutes(1));
		User user = new User("Ana", "ana@example.com", "hash");
		when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));

		limitedAuthService.forgotPassword("ana@example.com");

		assertThatThrownBy(() -> limitedAuthService.forgotPassword("ana@example.com"))
			.isInstanceOf(RateLimitExceededException.class);

		verify(passwordResetDispatcher, org.mockito.Mockito.times(1)).dispatch(user);
	}

	@Test
	void releasesForgotPasswordByEmailRateLimitAfterWindow() throws InterruptedException {
		AuthService limitedAuthService = newAuthServiceWithForgotPasswordByEmailLimit(userRepository,
				passwordResetDispatcher, 1, Duration.ofMillis(150));
		when(userRepository.findByEmail("nao-existe2@example.com")).thenReturn(Optional.empty());

		limitedAuthService.forgotPassword("nao-existe2@example.com");
		assertThatThrownBy(() -> limitedAuthService.forgotPassword("nao-existe2@example.com"))
			.isInstanceOf(RateLimitExceededException.class);

		Thread.sleep(300);

		limitedAuthService.forgotPassword("nao-existe2@example.com");
	}

	@Test
	void resetPasswordSavesTheNewHashAndDropsEverySession() {
		UUID userId = UUID.randomUUID();
		User user = new User("Ana", "ana@example.com", "hash-antigo");
		when(passwordResetService.consumeToken("raw-token")).thenReturn(userId);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(passwordEncoder.encode("senha-nova-1234")).thenReturn("hash-novo");

		authService.resetPassword("raw-token", "senha-nova-1234");

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(captor.capture());
		assertThat(captor.getValue().getPasswordHash()).isEqualTo("hash-novo");
		verify(refreshTokenService).revokeFamily(userId);

		ArgumentCaptor<PasswordChangedNoticeEvent> eventCaptor = ArgumentCaptor.forClass(PasswordChangedNoticeEvent.class);
		verify(eventPublisher).publishEvent(eventCaptor.capture());
		assertThat(eventCaptor.getValue().userId()).isEqualTo(userId);
		assertThat(eventCaptor.getValue().email()).isEqualTo("ana@example.com");
	}

	@Test
	void resetPasswordPropagatesAnInvalidTokenWithoutTouchingTheUser() {
		when(passwordResetService.consumeToken("token-invalido"))
			.thenThrow(new InvalidPasswordResetTokenException());

		assertThatThrownBy(() -> authService.resetPassword("token-invalido", "senha-nova-1234"))
			.isInstanceOf(InvalidPasswordResetTokenException.class);

		verify(userRepository, never()).save(any(User.class));
		verify(refreshTokenService, never()).revokeFamily(any());
		verify(eventPublisher, never()).publishEvent(any());
	}
}
