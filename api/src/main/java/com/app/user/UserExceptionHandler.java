package com.app.user;

import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.app.auth.EmailAlreadyExistsException;

/**
 * Handler da feature {@code com.app.user}. Existe por um motivo so: o
 * {@code AuthExceptionHandler} e {@code @RestControllerAdvice(basePackages = "com.app.auth")},
 * entao ele NAO aconselha os controllers desta feature - sem este advice, o
 * {@code EmailAlreadyExistsException} reusado pela edicao de perfil (D4) cairia no
 * {@code handleGeneric} do {@code GlobalExceptionHandler} e viraria {@code 500} em vez de
 * {@code 409}. O tipo da excecao continua sendo o de {@code com.app.auth}, para que o
 * contrato de colisao de e-mail seja o mesmo do cadastro.
 */
@RestControllerAdvice(basePackages = "com.app.user")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UserExceptionHandler {

	@ExceptionHandler(EmailAlreadyExistsException.class)
	public ResponseEntity<Map<String, String>> handleEmailAlreadyExists(EmailAlreadyExistsException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
	}
}
