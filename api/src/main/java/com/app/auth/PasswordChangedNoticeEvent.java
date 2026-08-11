package com.app.auth;

import java.util.UUID;

/**
 * Publicado dentro da transacao de PUT /api/auth/password e POST /api/auth/reset-password
 * (US-010). O e-mail/nome do titular viaja no evento em vez de o listener resolver o usuario
 * de novo - o listener so roda depois do commit, quando reabrir a sessao do Hibernate so para
 * ler dois campos seria desperdicio.
 */
public record PasswordChangedNoticeEvent(UUID userId, String email, String name) {
}
