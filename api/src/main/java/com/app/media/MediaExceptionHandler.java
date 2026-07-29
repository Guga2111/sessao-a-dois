package com.app.media;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.app.media")
public class MediaExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(MediaExceptionHandler.class);

	@ExceptionHandler(InvalidSearchQueryException.class)
	public ResponseEntity<Map<String, String>> handleInvalidSearchQuery(InvalidSearchQueryException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
	}

	@ExceptionHandler(InvalidMediaTypeException.class)
	public ResponseEntity<Map<String, String>> handleInvalidMediaType(InvalidMediaTypeException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
	}

	@ExceptionHandler(InvalidSortByException.class)
	public ResponseEntity<Map<String, String>> handleInvalidSortBy(InvalidSortByException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
	}

	@ExceptionHandler(MediaNotFoundException.class)
	public ResponseEntity<Map<String, String>> handleMediaNotFound(MediaNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
	}

	@ExceptionHandler(TmdbUnavailableException.class)
	public ResponseEntity<Map<String, String>> handleTmdbUnavailable(TmdbUnavailableException ex) {
		log.error(ex.getMessage(), ex.getCause());
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
			.body(Map.of("message", "Nao foi possivel buscar dados no momento. Tente novamente mais tarde."));
	}
}
