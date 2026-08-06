package com.app.security;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import io.jsonwebtoken.JwtException;

import com.app.auth.AuthCookieService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Le o cookie {@code access_token}, valida o JWT via {@link JwtService} e, se
 * valido, popula o {@link SecurityContextHolder} com o id do usuario (subject
 * do token) como principal.
 *
 * <p>Cookie ausente ou invalido/expirado/vazio simplesmente segue a cadeia
 * sem autenticacao - a decisao de rejeitar com 401 fica a cargo do
 * {@code authorizeHttpRequests}/{@code authenticationEntryPoint} configurados
 * em {@link SecurityConfig}. Sem fallback para o header {@code Authorization}
 * (decisao D1 do Epico 4 - cutover direto, sem janela de transicao).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtService jwtService;

	public JwtAuthenticationFilter(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		Cookie cookie = WebUtils.getCookie(request, AuthCookieService.ACCESS_TOKEN_COOKIE);

		if (cookie != null && StringUtils.hasText(cookie.getValue())) {
			try {
				UUID userId = jwtService.parseSubject(cookie.getValue());
				Authentication authentication = new UsernamePasswordAuthenticationToken(userId, null, List.of());
				SecurityContext context = SecurityContextHolder.createEmptyContext();
				context.setAuthentication(authentication);
				SecurityContextHolder.setContext(context);
			} catch (JwtException | IllegalArgumentException e) {
				SecurityContextHolder.clearContext();
			}
		}

		filterChain.doFilter(request, response);
	}
}
