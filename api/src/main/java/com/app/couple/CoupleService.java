package com.app.couple;

import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class CoupleService {

	private final CoupleRepository coupleRepository;
	private final InviteCodeGenerator inviteCodeGenerator;

	public CoupleService(CoupleRepository coupleRepository, InviteCodeGenerator inviteCodeGenerator) {
		this.coupleRepository = coupleRepository;
		this.inviteCodeGenerator = inviteCodeGenerator;
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

	private String generateUniqueInviteCode() {
		String code;
		do {
			code = inviteCodeGenerator.generate();
		} while (coupleRepository.existsByInviteCode(code));
		return code;
	}
}
