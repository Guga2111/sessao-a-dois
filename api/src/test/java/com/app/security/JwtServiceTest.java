package com.app.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cobre a validacao fail-fast de {@code app.jwt.secret} no construtor de
 * {@link JwtService}. Comportamento do token (gerar/parsear/expirar) fica
 * em stories futuras deste mesmo arquivo, nao aqui.
 */
class JwtServiceTest {

	private static final String VALID_SECRET = "a-valid-secret-with-at-least-32-bytes!!";

	@Test
	void throwsWhenSecretIsNull() {
		assertThatThrownBy(() -> new JwtService(null, 7))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("JWT_SECRET");
	}

	@Test
	void throwsWhenSecretIsBlank() {
		assertThatThrownBy(() -> new JwtService("   ", 7))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("JWT_SECRET");
	}

	@Test
	void throwsWhenSecretEqualsDevDefault() {
		assertThatThrownBy(() -> new JwtService("dev-only-secret-please-override-in-prod-32bytes+", 7))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("JWT_SECRET");
	}

	@Test
	void throwsWhenSecretIsShorterThan32Bytes() {
		assertThatThrownBy(() -> new JwtService("too-short-secret", 7))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("JWT_SECRET");
	}

	@Test
	void buildsNormallyWithValidSecret() {
		assertThat(VALID_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isGreaterThanOrEqualTo(32);
		assertThat(new JwtService(VALID_SECRET, 7)).isNotNull();
	}
}
