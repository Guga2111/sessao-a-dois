package com.app.observability;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /api/client-errors} (US-003). So {@code message} e
 * obrigatorio - os outros tres descrevem o erro de render o melhor que a
 * chamada conseguir, sem impedir o envio quando faltarem.
 */
public record ClientErrorRequest(

	@NotBlank(message = "message nao pode ser vazio")
	@Size(max = 500, message = "message deve ter no maximo 500 caracteres")
	String message,

	@Size(max = 4000, message = "stack deve ter no maximo 4000 caracteres")
	String stack,

	@Size(max = 300, message = "route deve ter no maximo 300 caracteres")
	String route,

	@Size(max = 300, message = "userAgent deve ter no maximo 300 caracteres")
	String userAgent
) {
}
