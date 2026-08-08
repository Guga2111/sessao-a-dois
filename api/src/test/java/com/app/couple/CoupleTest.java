package com.app.couple;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Comportamento de dominio da entidade Couple (epico 9, US-001). */
class CoupleTest {

	@Test
	void newCoupleIsActive() {
		Couple couple = new Couple(UUID.randomUUID(), "ABC1234");

		assertThat(couple.getDissolvedAt()).isNull();
		assertThat(couple.isActive()).isTrue();
	}

	@Test
	void dissolveRecordsTheInstantAndDeactivatesTheCouple() {
		Couple couple = new Couple(UUID.randomUUID(), "ABC1234");
		Instant before = Instant.now();

		couple.dissolve();

		assertThat(couple.getDissolvedAt()).isNotNull().isAfterOrEqualTo(before);
		assertThat(couple.isActive()).isFalse();
	}

	/** E9.17: sem isso, o codigo de um casal criado e nunca pareado continuaria valido depois de morto. */
	@Test
	void dissolveAlsoClearsTheInviteCode() {
		Couple couple = new Couple(UUID.randomUUID(), "ABC1234", Instant.now().plusSeconds(3600));

		couple.dissolve();

		assertThat(couple.getInviteCode()).isNull();
		assertThat(couple.getInviteCodeExpiresAt()).isNull();
		assertThat(couple.getDissolvedAt()).isNotNull();
	}

	/** E9.7: o metodo protege o proprio invariante em vez de ser no-op silencioso. */
	@Test
	void dissolveTwiceThrowsAndKeepsTheFirstInstant() {
		Couple couple = new Couple(UUID.randomUUID(), "ABC1234");
		couple.dissolve();
		Instant firstDissolvedAt = couple.getDissolvedAt();

		assertThatThrownBy(couple::dissolve).isInstanceOf(CoupleAlreadyDissolvedException.class);

		assertThat(couple.getDissolvedAt()).isEqualTo(firstDissolvedAt);
	}
}
