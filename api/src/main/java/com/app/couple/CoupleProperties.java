package com.app.couple;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propriedades do ciclo de vida do codigo de convite (US-007). O TTL e
 * aplicado por {@code CoupleService} ao criar/regenerar um convite.
 */
@Component
@ConfigurationProperties(prefix = "app.couple")
public class CoupleProperties {

	private Duration inviteCodeTtl = Duration.ofDays(7);

	public Duration getInviteCodeTtl() {
		return inviteCodeTtl;
	}

	public void setInviteCodeTtl(Duration inviteCodeTtl) {
		this.inviteCodeTtl = inviteCodeTtl;
	}
}
