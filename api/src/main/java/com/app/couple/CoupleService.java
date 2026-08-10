package com.app.couple;

import com.app.security.RateLimitExceededException;
import com.app.security.RateLimitProperties;
import com.app.security.RateLimitProperties.Limit;
import com.app.security.RateLimitService;
import com.app.security.RateLimitService.RateLimitResult;
import com.app.security.SecurityAuditLogger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CoupleService {

	private static final Logger log = LoggerFactory.getLogger(CoupleService.class);

	private final CoupleRepository coupleRepository;
	private final InviteCodeGenerator inviteCodeGenerator;
	private final RateLimitService rateLimitService;
	private final RateLimitProperties rateLimitProperties;
	private final CoupleProperties coupleProperties;
	private final SecurityAuditLogger securityAuditLogger;

	public CoupleService(CoupleRepository coupleRepository, InviteCodeGenerator inviteCodeGenerator,
			RateLimitService rateLimitService, RateLimitProperties rateLimitProperties,
			CoupleProperties coupleProperties, SecurityAuditLogger securityAuditLogger) {
		this.coupleRepository = coupleRepository;
		this.inviteCodeGenerator = inviteCodeGenerator;
		this.rateLimitService = rateLimitService;
		this.rateLimitProperties = rateLimitProperties;
		this.coupleProperties = coupleProperties;
		this.securityAuditLogger = securityAuditLogger;
	}

	public Couple createCouple(UUID userId) {
		if (coupleRepository.findActiveByUserId(userId).isPresent()) {
			throw new UserAlreadyInCoupleException();
		}

		Instant expiresAt = Instant.now().plus(coupleProperties.getInviteCodeTtl());
		Couple couple = new Couple(userId, generateUniqueInviteCode(), expiresAt);
		Couple saved = coupleRepository.save(couple);
		securityAuditLogger.coupleCreated(userId, saved.getId());
		return saved;
	}

	/**
	 * O casal ATIVO do usuario (epico 9): um casal dissolvido nunca e retornado, e a partir da US-001
	 * essa e a unica consulta de casal por usuario que existe - a derivada sem filtro que existia antes
	 * foi removida na US-002 justamente para que nao haja um segundo caminho sem o filtro.
	 */
	public Optional<Couple> getCurrentCouple(UUID userId) {
		return coupleRepository.findActiveByUserId(userId);
	}

	/**
	 * Os ids dos membros de um casal pelo id DELE, sem filtro de dissolucao: a busca ja parte de um
	 * casal identificado (o {@code couple_id} de um track/notificacao, que sobrevive a dissolucao pela
	 * D13) e o historico precisa continuar exibindo os nomes dos dois. Quem decide se o casal ainda vale
	 * para uma operacao e {@code getCurrentCouple}, antes de chegar aqui.
	 *
	 * Um {@code coupleId} inexistente devolve lista vazia com um {@code warn}: e estado impossivel pela
	 * FK, mas degradar a exibicao de nomes e melhor do que derrubar uma leitura de historico.
	 */
	public List<UUID> memberIds(UUID coupleId) {
		return coupleRepository.findById(coupleId)
			.map(Couple::memberIds)
			.orElseGet(() -> {
				log.warn("memberIds chamado para coupleId inexistente coupleId={}", coupleId);
				return List.of();
			});
	}

	/**
	 * Dissolve o casal ativo do usuario, se houver, e nao faz nada quando nao ha (epico 9, US-002).
	 * O no-op e deliberado: a exclusao de conta (US-007) passa por aqui e nao pode esbarrar na excecao
	 * da E9.7 so porque o usuario ja estava sem casal.
	 *
	 * @return o id do casal dissolvido, ou vazio quando nao havia casal ativo.
	 */
	public Optional<UUID> dissolveIfActive(UUID userId) {
		return coupleRepository.findActiveByUserId(userId).map(couple -> {
			couple.dissolve();
			return coupleRepository.save(couple).getId();
		});
	}

	/**
	 * Dissolve o casal ativo do usuario a pedido dele (epico 9, US-004). Diferente de
	 * {@link #dissolveIfActive}, este e o caminho do endpoint: nao ha casal ativo -> {@code 404}, e nunca
	 * um {@code 500} pela excecao da E9.7 (um casal ja dissolvido nao e "ativo", entao a segunda chamada
	 * cai no mesmo 404 da primeira sem chegar em {@code dissolve()}).
	 *
	 * A acao e UNILATERAL por decisao de produto: qualquer um dos dois membros dissolve, sem confirmacao
	 * do parceiro.
	 *
	 * @return o id do casal dissolvido.
	 */
	public UUID dissolveCouple(UUID userId) {
		enforceDissolveRateLimit(userId);

		Couple couple = coupleRepository.findActiveByUserId(userId)
			.orElseThrow(CoupleNotFoundException::new);

		couple.dissolve();
		Couple saved = coupleRepository.save(couple);
		securityAuditLogger.coupleDissolved(userId, saved.getId());
		return saved.getId();
	}

	public Couple joinCouple(UUID userId, String inviteCode) {
		enforceJoinRateLimit(userId);

		Couple couple = coupleRepository.findActiveByInviteCode(inviteCode)
			.orElseThrow(InviteCodeNotFoundException::new);

		Instant expiresAt = couple.getInviteCodeExpiresAt();
		if (expiresAt != null && Instant.now().isAfter(expiresAt)) {
			throw new InviteCodeExpiredException();
		}

		if (couple.getUser1Id().equals(userId)) {
			throw new CannotJoinOwnCoupleException();
		}

		if (coupleRepository.findActiveByUserId(userId).isPresent()) {
			throw new UserAlreadyInCoupleException();
		}

		if (couple.getUser2Id() != null) {
			throw new CoupleAlreadyFullException();
		}

		couple.setUser2Id(userId);
		couple.clearInviteCode();
		Couple saved = coupleRepository.save(couple);
		securityAuditLogger.coupleJoined(userId, saved.getId());
		return saved;
	}

	/**
	 * Regenera o codigo de convite do casal (US-009). So o criador (user1Id) pode regenerar, e apenas
	 * enquanto o casal ainda nao estiver pareado - o codigo antigo deixa de funcionar imediatamente.
	 */
	public Couple regenerateInviteCode(UUID userId) {
		enforceRegenerateInviteCodeRateLimit(userId);

		Couple couple = coupleRepository.findActiveByUserId(userId)
			.orElseThrow(CoupleNotFoundException::new);

		if (!couple.getUser1Id().equals(userId)) {
			throw new NotCoupleCreatorException();
		}

		if (couple.getUser2Id() != null) {
			throw new CoupleAlreadyFullException();
		}

		Instant expiresAt = Instant.now().plus(coupleProperties.getInviteCodeTtl());
		couple.regenerateInviteCode(generateUniqueInviteCode(), expiresAt);
		Couple saved = coupleRepository.save(couple);
		securityAuditLogger.inviteCodeRegenerated(userId, saved.getId());
		return saved;
	}

	/** Limite por usuario autenticado (US-003), alem do limite por IP ja aplicado pelo {@code RateLimitFilter} (US-002). */
	private void enforceJoinRateLimit(UUID userId) {
		Limit limit = rateLimitProperties.getCoupleJoinByUser();
		RateLimitResult result = rateLimitService.tryConsume("couple-join:user:" + userId, limit.getCapacity(),
				limit.getWindow());
		if (!result.allowed()) {
			throw new RateLimitExceededException(result.retryAfterSeconds());
		}
	}

	/** Reaproveita a mesma infra/limite por usuario da US-003 (US-009), com chave propria. */
	private void enforceRegenerateInviteCodeRateLimit(UUID userId) {
		Limit limit = rateLimitProperties.getCoupleJoinByUser();
		RateLimitResult result = rateLimitService.tryConsume("invite-code-regenerate:user:" + userId,
				limit.getCapacity(), limit.getWindow());
		if (!result.allowed()) {
			throw new RateLimitExceededException(result.retryAfterSeconds());
		}
	}

	/**
	 * Limite POR USUARIO da dissolucao (US-004). Tem {@code Limit} proprio ({@code app.rate-limit.couple-dissolve},
	 * 5/hora) em vez de reaproveitar o do join: dissolver e destrutivo para o vinculo e nao deve herdar a
	 * capacidade de uma operacao de entrada. Nao passa pelo {@code RateLimitFilter}, que chaveia por IP.
	 */
	private void enforceDissolveRateLimit(UUID userId) {
		Limit limit = rateLimitProperties.getCoupleDissolve();
		RateLimitResult result = rateLimitService.tryConsume("couple-dissolve:user:" + userId, limit.getCapacity(),
				limit.getWindow());
		if (!result.allowed()) {
			throw new RateLimitExceededException(result.retryAfterSeconds());
		}
	}

	private String generateUniqueInviteCode() {
		String code;
		do {
			code = inviteCodeGenerator.generate();
		} while (coupleRepository.existsByInviteCode(code));
		return code;
	}
}
