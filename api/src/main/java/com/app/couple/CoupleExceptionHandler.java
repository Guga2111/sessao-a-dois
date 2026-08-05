package com.app.couple;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.app.security.RateLimitExceededException;

@RestControllerAdvice(basePackages = "com.app.couple")
public class CoupleExceptionHandler {

	@ExceptionHandler(UserAlreadyInCoupleException.class)
	public ResponseEntity<Map<String, String>> handleUserAlreadyInCouple(UserAlreadyInCoupleException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
	}

	@ExceptionHandler(CoupleAlreadyFullException.class)
	public ResponseEntity<Map<String, String>> handleCoupleAlreadyFull(CoupleAlreadyFullException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
	}

	@ExceptionHandler(InviteCodeNotFoundException.class)
	public ResponseEntity<Map<String, String>> handleInviteCodeNotFound(InviteCodeNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
	}

	@ExceptionHandler(InviteCodeExpiredException.class)
	public ResponseEntity<Map<String, String>> handleInviteCodeExpired(InviteCodeExpiredException ex) {
		return ResponseEntity.status(HttpStatus.GONE).body(Map.of("message", ex.getMessage()));
	}

	@ExceptionHandler(CannotJoinOwnCoupleException.class)
	public ResponseEntity<Map<String, String>> handleCannotJoinOwnCouple(CannotJoinOwnCoupleException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
	}

	@ExceptionHandler(CoupleNotFoundException.class)
	public ResponseEntity<Map<String, String>> handleCoupleNotFound(CoupleNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
	}

	@ExceptionHandler(NotCoupleCreatorException.class)
	public ResponseEntity<Map<String, String>> handleNotCoupleCreator(NotCoupleCreatorException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
	}

	@ExceptionHandler(RateLimitExceededException.class)
	public ResponseEntity<Map<String, String>> handleRateLimitExceeded(RateLimitExceededException ex) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
			.header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
			.body(Map.of("message", ex.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			fieldErrors.put(error.getField(), error.getDefaultMessage());
		}

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("message", "dados invalidos");
		body.put("errors", fieldErrors);
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
	}
}
