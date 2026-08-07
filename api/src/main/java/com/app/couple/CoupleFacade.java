package com.app.couple;

import com.app.common.ResourceNotFoundException;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de {@code com.app.couple} para as demais features (epico 9, US-002).
 *
 * A regra de "quem sao os dois" e de "este casal ainda vale" continua sendo do {@link CoupleService};
 * esta classe e uma fachada fina sobre ele, nao uma segunda implementacao. O que ela acrescenta e o
 * contrato: <b>so identificadores cruzam a fronteira</b> (E9.4) - nenhum metodo aqui aceita, devolve ou
 * expoe a entidade {@link Couple}, para que {@code tracking}/{@code match} nao voltem a depender do
 * mapeamento interno do casal nem consigam contornar o filtro de casal dissolvido.
 *
 * {@code com.app.websocket} fica de fora deste desacoplamento por decisao explicita (E9.19): ele
 * continua usando {@link CoupleService#getCurrentCouple} e herda o filtro de graca.
 */
@Component
public class CoupleFacade {

	private final CoupleService coupleService;

	public CoupleFacade(CoupleService coupleService) {
		this.coupleService = coupleService;
	}

	/** O id do casal ATIVO do usuario, ou vazio quando ele nao tem casal (ou o casal foi dissolvido). */
	public Optional<UUID> findActiveCoupleId(UUID userId) {
		return coupleService.getCurrentCouple(userId).map(Couple::getId);
	}

	/**
	 * Igual a {@link #findActiveCoupleId}, mas para os caminhos que so fazem sentido com casal: lanca
	 * {@link ResourceNotFoundException} (E9.16), preservando exatamente o 404 que {@code tracking} e
	 * {@code match} ja devolvem hoje quando o usuario nao tem casal.
	 */
	public UUID requireActiveCoupleId(UUID userId) {
		return findActiveCoupleId(userId)
			.orElseThrow(() -> new ResourceNotFoundException("usuario nao pertence a nenhum casal"));
	}

	/**
	 * Os ids dos membros do casal, pulando um parceiro ainda inexistente - a semantica que antes vivia em
	 * {@code MediaTrackMapper.memberIds(Couple)}. Resolve pelo id do casal (nao do usuario) porque o
	 * historico de um casal dissolvido continua precisando dos nomes dos dois.
	 */
	public List<UUID> memberIds(UUID coupleId) {
		return coupleService.memberIds(coupleId);
	}

	/**
	 * Desfaz o vinculo do usuario quando ha um casal ativo, e e NO-OP quando nao ha - a exclusao de conta
	 * (US-007) chama isto sem saber se a pessoa tem casal e nao pode esbarrar na excecao da E9.7.
	 */
	public void dissolveIfActive(UUID userId) {
		coupleService.dissolveIfActive(userId);
	}
}
