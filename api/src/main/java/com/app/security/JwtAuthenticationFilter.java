package com.app.security;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.JwtException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Le o header {@code Authorization: Bearer <token>}, valida o JWT via
 * {@link JwtService} e, se valido, popula o {@link SecurityContextHolder} com
 * o id do usuario (subject do token) como principal.
 *
 * <p>Token ausente ou invalido/expirado simplesmente segue a cadeia sem
 * autenticacao - a decisao de rejeitar com 401 fica a cargo do
 * {@code authorizeHttpRequests}/{@code authenticationEntryPoint} configurados
 * em {@link SecurityConfig}.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtService jwtService;

	public JwtAuthenticationFilter(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String header = request.getHeader("Authorization");

		if (header != null && header.startsWith(BEARER_PREFIX)) {
			String token = header.substring(BEARER_PREFIX.length());
			try {
				UUID userId = jwtService.parseSubject(token);
				Authentication authentication = new UsernamePasswordAuthenticationToken(userId, null, List.of());
				SecurityContextHolder.getContext().setAuthentication(authentication);
			} catch (JwtException | IllegalArgumentException e) {
				SecurityContextHolder.clearContext();
			}
		}

		filterChain.doFilter(request, response);
	}
}
