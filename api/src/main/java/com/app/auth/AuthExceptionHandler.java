package com.app.auth;

import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.app.auth")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthExceptionHandler {

	@ExceptionHandler(EmailAlreadyExistsException.class)
	public ResponseEntity<Map<String, String>> handleEmailAlreadyExists(EmailAlreadyExistsException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	public ResponseEntity<Map<String, String>> handleInvalidCredentials(InvalidCredentialsException ex) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", ex.getMessage()));
	}

	@ExceptionHandler(InvalidRefreshTokenException.class)
	public ResponseEntity<Map<String, String>> handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", ex.getMessage()));
	}

	@ExceptionHandler(RefreshReuseDetectedException.class)
	public ResponseEntity<Map<String, String>> handleRefreshReuseDetected(RefreshReuseDetectedException ex) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", ex.getMessage()));
	}

	@ExceptionHandler(InvalidPasswordResetTokenException.class)
	public ResponseEntity<Map<String, String>> handleInvalidPasswordResetToken(InvalidPasswordResetTokenException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
	}
}
