package com.app.couple;

import java.time.Instant;
import java.util.UUID;

public record CoupleResponse(UUID id, String inviteCode, Instant inviteCodeExpiresAt, PartnerSummary partner,
		Instant createdAt) {
}
