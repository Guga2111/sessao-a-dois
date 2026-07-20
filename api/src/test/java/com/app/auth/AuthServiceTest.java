package com.app.auth;

import com.app.user.User;
import com.app.user.UserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

	private AuthService authService;

	@Test
	void registersUserWithHashedPassword() {
		authService = new AuthService(userRepository, passwordEncoder);
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
		authService = new AuthService(userRepository, passwordEncoder);
		var request = new RegisterRequest("Ana", "ana@example.com", "senha1234");

		when(userRepository.existsByEmail("ana@example.com")).thenReturn(true);

		assertThatThrownBy(() -> authService.register(request))
			.isInstanceOf(EmailAlreadyExistsException.class);

		verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
		verify(passwordEncoder, never()).encode(anyString());
	}
}
