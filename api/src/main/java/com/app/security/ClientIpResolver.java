package com.app.security;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Unica implementacao da resolucao do IP real do cliente na aplicacao - le o
 * primeiro IP de X-Forwarded-For (o nginx sempre envia esse header em
 * producao) e cai em request.getRemoteAddr() quando o header esta ausente
 * ou em branco.
 */
@Component
public class ClientIpResolver {

	public String resolve(HttpServletRequest request) {
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (forwardedFor == null || forwardedFor.isBlank()) {
			return request.getRemoteAddr();
		}
		return forwardedFor.split(",")[0].trim();
	}
}
