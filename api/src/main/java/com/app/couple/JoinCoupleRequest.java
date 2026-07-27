package com.app.couple;

import jakarta.validation.constraints.NotBlank;

public record JoinCoupleRequest(
	@NotBlank(message = "codigo de convite nao pode ser vazio")
	String inviteCode
) {
}
