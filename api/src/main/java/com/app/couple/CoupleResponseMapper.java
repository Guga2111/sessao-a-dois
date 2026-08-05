package com.app.couple;

import com.app.user.UserRepository;

import org.springframework.stereotype.Component;

import java.util.UUID;

/** Unica implementacao de Couple para CoupleResponse, reutilizada por AuthController e CoupleController. */
@Component
public class CoupleResponseMapper {

	private final UserRepository userRepository;

	public CoupleResponseMapper(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public CoupleResponse toResponse(Couple couple, UUID currentUserId) {
		UUID partnerId = couple.getUser1Id().equals(currentUserId) ? couple.getUser2Id() : couple.getUser1Id();
		PartnerSummary partner = partnerId == null ? null : userRepository.findById(partnerId)
			.map(user -> new PartnerSummary(user.getId(), user.getName()))
			.orElse(null);
		return new CoupleResponse(couple.getId(), couple.getInviteCode(), couple.getInviteCodeExpiresAt(), partner,
				couple.getCreatedAt());
	}
}
