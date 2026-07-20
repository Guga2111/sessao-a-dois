package com.app.media;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.app.media")
public class MediaExceptionHandler {

	@ExceptionHandler(InvalidSearchQueryException.class)
	public ResponseEntity<Map<String, String>> handleInvalidSearchQuery(InvalidSearchQueryException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
	}
}
