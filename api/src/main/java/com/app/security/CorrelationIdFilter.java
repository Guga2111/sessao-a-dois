package com.app.security;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Gera (ou reaproveita, via header {@code X-Request-Id}) um correlation id
 * por requisicao e o coloca no MDC sob {@link #MDC_KEY}, para que todo
 * padrao de log da aplicacao possa incluir {@code %X{correlationId}}. Roda
 * antes de qualquer outro filtro (registrado com {@code Ordered.HIGHEST_PRECEDENCE}
 * em {@link SecurityConfig#correlationIdFilterRegistration}), para que ate as
 * linhas de log do {@link RateLimitFilter} (US-012) ja carreguem o id.
 *
 * <p>O MDC e sempre limpo no {@code finally}, inclusive quando a cadeia
 * lanca excecao - senao o valor vaza para a proxima requisicao atendida pela
 * mesma thread do pool do Tomcat. O id tambem e devolvido ao cliente no
 * mesmo header {@code X-Request-Id} da resposta.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

	public static final String HEADER_NAME = "X-Request-Id";
	public static final String MDC_KEY = "correlationId";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String incoming = request.getHeader(HEADER_NAME);
		String correlationId = StringUtils.hasText(incoming) ? incoming : UUID.randomUUID().toString();

		MDC.put(MDC_KEY, correlationId);
		response.setHeader(HEADER_NAME, correlationId);
		try {
			filterChain.doFilter(request, response);
		} finally {
			MDC.remove(MDC_KEY);
		}
	}
}
