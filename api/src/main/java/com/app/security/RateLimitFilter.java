package com.app.security;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.app.security.RateLimitProperties.Limit;
import com.app.security.RateLimitService.RateLimitResult;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Aplica rate limit por IP a
 * login/register/refresh/couple-join/forgot-password/reset-password/client-errors ANTES da
 * cadeia do Spring Security (registrado com @Order baixo em
 * {@link SecurityConfig#rateLimitFilterRegistration}, ao contrario de
 * {@link SecurityConfig#jwtFilterRegistration}, que desliga o filtro na
 * cadeia de servlet padrao por design) - uma requisicao bloqueada aqui nao
 * chega a consumir BCrypt nem banco.
 *
 * <p>Com {@code app.rate-limit.enabled=false} o filtro deixa tudo passar sem
 * contar. Excesso de requisicoes retorna 429 com header {@code Retry-After}
 * em segundos e corpo {@code {"message": "..."}}, no mesmo formato dos
 * demais erros da aplicacao.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

	private final RateLimitService rateLimitService;
	private final RateLimitProperties properties;
	private final ClientIpResolver clientIpResolver;
	private final ObjectMapper objectMapper;
	private final SecurityAuditLogger securityAuditLogger;

	public RateLimitFilter(RateLimitService rateLimitService, RateLimitProperties properties,
			ClientIpResolver clientIpResolver, ObjectMapper objectMapper, SecurityAuditLogger securityAuditLogger) {
		this.rateLimitService = rateLimitService;
		this.properties = properties;
		this.clientIpResolver = clientIpResolver;
		this.objectMapper = objectMapper;
		this.securityAuditLogger = securityAuditLogger;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		if (!properties.isEnabled()) {
			filterChain.doFilter(request, response);
			return;
		}

		Endpoint endpoint = Endpoint.match(request);
		if (endpoint == null) {
			filterChain.doFilter(request, response);
			return;
		}

		String ip = clientIpResolver.resolve(request);
		Limit limit = endpoint.limit(properties);
		RateLimitResult result = rateLimitService.tryConsume(endpoint.key() + ":ip:" + ip, limit.getCapacity(),
				limit.getWindow());

		if (result.allowed()) {
			filterChain.doFilter(request, response);
			return;
		}

		securityAuditLogger.rateLimitExceeded(endpoint.key(), ip);
		writeTooManyRequests(response, result.retryAfterSeconds());
	}

	private void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds) throws IOException {
		response.setStatus(429);
		response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
		response.setContentType("application/json");
		objectMapper.writeValue(response.getWriter(),
				Map.of("message", "limite de requisicoes excedido, tente novamente mais tarde"));
	}

	private enum Endpoint {

		LOGIN("POST", "/api/auth/login") {
			@Override
			Limit limit(RateLimitProperties properties) {
				return properties.getLogin();
			}
		},
		REGISTER("POST", "/api/auth/register") {
			@Override
			Limit limit(RateLimitProperties properties) {
				return properties.getRegister();
			}
		},
		REFRESH("POST", "/api/auth/refresh") {
			@Override
			Limit limit(RateLimitProperties properties) {
				return properties.getRefresh();
			}
		},
		COUPLE_JOIN("POST", "/api/couple/join") {
			@Override
			Limit limit(RateLimitProperties properties) {
				return properties.getCoupleJoin();
			}
		},
		FORGOT_PASSWORD("POST", "/api/auth/forgot-password") {
			@Override
			Limit limit(RateLimitProperties properties) {
				return properties.getForgotPassword();
			}
		},
		RESET_PASSWORD("POST", "/api/auth/reset-password") {
			@Override
			Limit limit(RateLimitProperties properties) {
				return properties.getResetPassword();
			}
		},
		CLIENT_ERRORS("POST", "/api/client-errors") {
			@Override
			Limit limit(RateLimitProperties properties) {
				return properties.getClientErrors();
			}
		};

		private final String method;
		private final String path;

		Endpoint(String method, String path) {
			this.method = method;
			this.path = path;
		}

		abstract Limit limit(RateLimitProperties properties);

		String key() {
			return path;
		}

		static Endpoint match(HttpServletRequest request) {
			for (Endpoint endpoint : values()) {
				if (endpoint.method.equals(request.getMethod()) && endpoint.path.equals(request.getRequestURI())) {
					return endpoint;
				}
			}
			return null;
		}
	}
}
