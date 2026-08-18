package com.app.observability;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/client-errors} (US-003): recebe o erro de render que o
 * error boundary do frontend captura (US-006) e o entrega ao
 * {@link ClientErrorLogger}, para investigar depois um "ficou tudo branco"
 * relatado pelo usuario. Publico e sem sessao (E12.5) - nada do que chega
 * aqui volta na resposta, nem em caso de erro de validacao
 * ({@code com.app.common.GlobalExceptionHandler} ja garante isso).
 */
@RestController
@RequestMapping("/api/client-errors")
public class ClientErrorController {

	private final ClientErrorLogger clientErrorLogger;

	public ClientErrorController(ClientErrorLogger clientErrorLogger) {
		this.clientErrorLogger = clientErrorLogger;
	}

	@PostMapping
	public ResponseEntity<Void> report(@Valid @RequestBody ClientErrorRequest request) {
		clientErrorLogger.log(request);
		return ResponseEntity.noContent().build();
	}
}
