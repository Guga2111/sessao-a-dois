package com.app.common;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.app.security.RateLimitExceededException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Controller minimalista usado apenas por {@link GlobalExceptionHandlerTest} para
 * exercitar cada tipo de excecao tratado por {@link GlobalExceptionHandler} atraves
 * de uma requisicao HTTP real. Vive em src/test - nunca e registrado em producao.
 */
@RestController
@RequestMapping("/test-global-exceptions")
class GlobalExceptionHandlerTestSupportController {

	@GetMapping("/not-found")
	void notFound() {
		throw new ResourceNotFoundException("recurso nao encontrado");
	}

	@GetMapping("/access-denied")
	void accessDenied() {
		throw new AccessDeniedException("acesso negado");
	}

	@GetMapping("/rate-limited")
	void rateLimited() {
		throw new RateLimitExceededException(30);
	}

	@GetMapping("/boom")
	void boom() {
		throw new RuntimeException("detalhe interno sensivel que nao deve vazar para o cliente");
	}

	@PostMapping("/validate")
	void validate(@Valid @RequestBody Payload payload) {
	}

	record Payload(@NotBlank(message = "nao pode ser vazio") String name) {
	}
}
