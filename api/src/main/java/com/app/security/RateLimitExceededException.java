package com.app.security;

/**
 * Lancada quando um limite de rate limit por conta (e-mail no login, usuario
 * no couple-join - US-003) e excedido na camada de servico, ao contrario dos
 * limites por IP da US-002 que sao aplicados em {@link RateLimitFilter} antes
 * de chegar ao servico. Carrega o {@code Retry-After} para que o handler
 * responda com o mesmo formato 429 do filtro.
 */
public class RateLimitExceededException extends RuntimeException {

	private final long retryAfterSeconds;

	public RateLimitExceededException(long retryAfterSeconds) {
		super("limite de requisicoes excedido, tente novamente mais tarde");
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}
}
