package com.app.couple;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InviteCodeGeneratorTest {

	private final InviteCodeGenerator generator = new InviteCodeGenerator();

	@Test
	void generatesCodeWithFixedLengthOfEightCharacters() {
		for (int i = 0; i < 50; i++) {
			String code = generator.generate();
			assertThat(code).hasSize(8);
		}
	}

	@Test
	void generatesCodeWithoutAmbiguousCharacters() {
		for (int i = 0; i < 50; i++) {
			String code = generator.generate();
			assertThat(code).doesNotContainAnyWhitespaces();
			assertThat(code).matches("[A-Z0-9]+");
			assertThat(code).doesNotContain("0", "O", "1", "I", "L");
		}
	}
}
