package com.app.couple;

import java.util.UUID;

/**
 * Lancada por {@link Couple#dissolve()} quando o casal ja esta dissolvido (E9.7).
 *
 * Deliberadamente NAO tem tratamento no {@code CoupleExceptionHandler}: nenhum fluxo de usuario deve
 * conseguir chegar aqui - um casal dissolvido ja nao e retornado por {@code findActiveByUserId}, entao
 * o endpoint responde 404 antes. Se esta excecao aparecer num log de producao, e bug interno (dois
 * {@code dissolve()} na mesma transacao, por exemplo), e um 500 e a resposta correta.
 */
public class CoupleAlreadyDissolvedException extends IllegalStateException {

	public CoupleAlreadyDissolvedException(UUID coupleId) {
		super("Casal ja dissolvido: " + coupleId);
	}
}
