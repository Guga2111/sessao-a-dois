package com.app.match;

import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.app.match")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MatchExceptionHandler {

	@ExceptionHandler(TitleAlreadyTrackedException.class)
	public ResponseEntity<Map<String, String>> handleTitleAlreadyTracked(TitleAlreadyTrackedException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
	}
}
