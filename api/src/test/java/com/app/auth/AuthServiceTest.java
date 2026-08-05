package com.app.auth;

import com.app.security.JwtService;
import com.app.security.RateLimitExceededException;
import com.app.security.RateLimitProperties;
import com.app.security.RateLimitService;
import com.app.user.User;
import com.app.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

	private RateLimitProperties rateLimitProperties;

	private AuthService authService;

	@BeforeEach
	void setUp() {
		rateLimitProperties = new RateLimitProperties();
		authService = new AuthService(userRepository, passwordEncoder, jwtService, refreshTokenService,
				new RateLimitService(), rateLimitProperties);
	}

	private static AuthService newAuthServiceWithLoginByEmailLimit(UserRepository userRepository,
			PasswordEncoder passwordEncoder, JwtService jwtService, RefreshTokenService refreshTokenService,
			int capacity, Duration window) {
		RateLimitProperties properties = new RateLimitProperties();
		properties.setLoginByEmail(new RateLimitProperties.Limit(capacity, window));
		return new AuthService(userRepository, passwordEncoder, jwtService, refreshTokenService,
				new RateLimitService(), properties);
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

		verify(passwordEncoder, never()).matches(anyString(), anyString());
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

		verify(passwordEncoder, never()).matches(anyString(), anyString());
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
	void refreshPropagatesRotationFailure() {
		when(refreshTokenService.rotate(anyString(), any(), any())).thenThrow(new RefreshReuseDetectedException());

		assertThatThrownBy(() -> authService.refresh("reused-token", "Mozilla/5.0", "203.0.113.1"))
			.isInstanceOf(RefreshReuseDetectedException.class);

		verify(jwtService, never()).generateToken(any());
	}
}
