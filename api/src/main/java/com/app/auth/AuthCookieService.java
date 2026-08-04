package com.app.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Centraliza a construcao dos cookies HttpOnly de sessao (access_token e
 * refresh_token) para que emissao (login), rotacao e limpeza usem exatamente
 * as mesmas flags - nenhum desses fluxos monta um ResponseCookie por conta
 * propria.
 */
@Service
public class AuthCookieService {

	public static final String ACCESS_TOKEN_COOKIE = "access_token";
	public static final String REFRESH_TOKEN_COOKIE = "refresh_token";
	private static final String REFRESH_TOKEN_PATH = "/api/auth/refresh";

	private final boolean secure;
	private final Duration accessTokenTtl;
	private final Duration refreshTokenTtl;

	public AuthCookieService(
			@Value("${app.auth.cookie.secure:true}") boolean secure,
			@Value("${app.jwt.access-token-ttl:15m}") Duration accessTokenTtl,
			@Value("${app.auth.refresh-token-ttl:30d}") Duration refreshTokenTtl) {
		this.secure = secure;
		this.accessTokenTtl = accessTokenTtl;
		this.refreshTokenTtl = refreshTokenTtl;
	}

	/** access_token usa Path=/ (decisao E1) para chegar tambem ao handshake em /ws. */
	public ResponseCookie accessTokenCookie(String token) {
		return build(ACCESS_TOKEN_COOKIE, token, "/", accessTokenTtl);
	}

	/** refresh_token usa Path restrito - nunca e enviado numa requisicao normal. */
	public ResponseCookie refreshTokenCookie(String token) {
		return build(REFRESH_TOKEN_COOKIE, token, REFRESH_TOKEN_PATH, refreshTokenTtl);
	}

	private ResponseCookie build(String name, String value, String path, Duration maxAge) {
		return ResponseCookie.from(name, value)
			.httpOnly(true)
			.secure(secure)
			.sameSite("Strict")
			.path(path)
			.maxAge(maxAge)
			.build();
	}
}
