package com.app.auth;

import com.app.user.User;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
		User user = authService.register(request);
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(new RegisterResponse(user.getId(), user.getName(), user.getEmail()));
	}

	@PostMapping("/login")
	public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
		AuthService.LoginResult result = authService.login(request);
		User user = result.user();
		UserSummary userSummary = new UserSummary(user.getId(), user.getName(), user.getEmail());
		return ResponseEntity.ok(new LoginResponse(result.token(), userSummary, null));
	}
}
