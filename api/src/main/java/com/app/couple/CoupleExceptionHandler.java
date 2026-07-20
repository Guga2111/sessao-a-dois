package com.app.couple;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.app.couple")
public class CoupleExceptionHandler {

	@ExceptionHandler(UserAlreadyInCoupleException.class)
	public ResponseEntity<Map<String, String>> handleUserAlreadyInCouple(UserAlreadyInCoupleException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
	}
}
