package com.app.auth;

import com.app.security.JwtService;
import com.app.user.User;
import com.app.user.UserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

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

	private AuthService authService;

	@Test
	void registersUserWithHashedPassword() {
		authService = new AuthService(userRepository, passwordEncoder, jwtService, refreshTokenService);
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
		authService = new AuthService(userRepository, passwordEncoder, jwtService, refreshTokenService);
		var request = new RegisterRequest("Ana", "ana@example.com", "senha1234");

		when(userRepository.existsByEmail("ana@example.com")).thenReturn(true);

		assertThatThrownBy(() -> authService.register(request))
			.isInstanceOf(EmailAlreadyExistsException.class);

		verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
		verify(passwordEncoder, never()).encode(anyString());
	}

	@Test
	void logsInSuccessfullyAndReturnsTokens() {
		authService = new AuthService(userRepository, passwordEncoder, jwtService, refreshTokenService);
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
		authService = new AuthService(userRepository, passwordEncoder, jwtService, refreshTokenService);
		var request = new LoginRequest("ana@example.com", "wrong-password");
		User user = new User("Ana", "ana@example.com", "hashed-password");

		when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

		assertThatThrownBy(() -> authService.login(request, "Mozilla/5.0", "203.0.113.1"))
			.isInstanceOf(InvalidCredentialsException.class);
	}

	@Test
	void rejectsLoginWithUnknownEmail() {
		authService = new AuthService(userRepository, passwordEncoder, jwtService, refreshTokenService);
		var request = new LoginRequest("desconhecido@example.com", "senha1234");

		when(userRepository.findByEmail("desconhecido@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login(request, "Mozilla/5.0", "203.0.113.1"))
			.isInstanceOf(InvalidCredentialsException.class);

		verify(passwordEncoder, never()).matches(anyString(), anyString());
	}

	@Test
	void refreshRotatesTokenAndIssuesNewAccessTokenForRotatedUser() {
		authService = new AuthService(userRepository, passwordEncoder, jwtService, refreshTokenService);
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
		authService = new AuthService(userRepository, passwordEncoder, jwtService, refreshTokenService);

		authService.logout("raw-refresh-token");

		verify(refreshTokenService).revoke("raw-refresh-token");
	}

	@Test
	void refreshPropagatesRotationFailure() {
		authService = new AuthService(userRepository, passwordEncoder, jwtService, refreshTokenService);
		when(refreshTokenService.rotate(anyString(), any(), any())).thenThrow(new RefreshReuseDetectedException());

		assertThatThrownBy(() -> authService.refresh("reused-token", "Mozilla/5.0", "203.0.113.1"))
			.isInstanceOf(RefreshReuseDetectedException.class);

		verify(jwtService, never()).generateToken(any());
	}
}
