package com.app.couple;

import com.app.security.RateLimitExceededException;
import com.app.security.RateLimitProperties;
import com.app.security.RateLimitProperties.Limit;
import com.app.security.RateLimitService;
import com.app.security.RateLimitService.RateLimitResult;

import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class CoupleService {

	private final CoupleRepository coupleRepository;
	private final InviteCodeGenerator inviteCodeGenerator;
	private final RateLimitService rateLimitService;
	private final RateLimitProperties rateLimitProperties;

	public CoupleService(CoupleRepository coupleRepository, InviteCodeGenerator inviteCodeGenerator,
			RateLimitService rateLimitService, RateLimitProperties rateLimitProperties) {
		this.coupleRepository = coupleRepository;
		this.inviteCodeGenerator = inviteCodeGenerator;
		this.rateLimitService = rateLimitService;
		this.rateLimitProperties = rateLimitProperties;
	}

	public Couple createCouple(UUID userId) {
		if (coupleRepository.findByUser1IdOrUser2Id(userId, userId).isPresent()) {
			throw new UserAlreadyInCoupleException();
		}

		Couple couple = new Couple(userId, generateUniqueInviteCode());
		return coupleRepository.save(couple);
	}

	public Optional<Couple> getCurrentCouple(UUID userId) {
		return coupleRepository.findByUser1IdOrUser2Id(userId, userId);
	}

	public Couple joinCouple(UUID userId, String inviteCode) {
		enforceJoinRateLimit(userId);

		Couple couple = coupleRepository.findByInviteCode(inviteCode)
			.orElseThrow(InviteCodeNotFoundException::new);

		if (couple.getUser1Id().equals(userId)) {
			throw new CannotJoinOwnCoupleException();
		}

		if (coupleRepository.findByUser1IdOrUser2Id(userId, userId).isPresent()) {
			throw new UserAlreadyInCoupleException();
		}

		if (couple.getUser2Id() != null) {
			throw new CoupleAlreadyFullException();
		}

		couple.setUser2Id(userId);
		return coupleRepository.save(couple);
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

	private String generateUniqueInviteCode() {
		String code;
		do {
			code = inviteCodeGenerator.generate();
		} while (coupleRepository.existsByInviteCode(code));
		return code;
	}
}
