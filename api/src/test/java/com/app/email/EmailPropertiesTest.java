package com.app.email;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailPropertiesTest {

	@Test
	void validate_failsFastWhenResendProviderHasNoApiKey() {
		EmailProperties properties = propertiesWith("resend", "", "no-reply@sessaoadois.luisgosampaio.com",
			"https://sessaoadois.luisgosampaio.com");

		assertThatThrownBy(properties::validate)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("RESEND_API_KEY");
	}

	@Test
	void validate_failsFastWhenResendProviderHasNoFrom() {
		EmailProperties properties = propertiesWith("resend", "fake-api-key", "  ",
			"https://sessaoadois.luisgosampaio.com");

		assertThatThrownBy(properties::validate)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("EMAIL_FROM");
	}

	@Test
	void validate_failsFastWhenPublicUrlIsNotAbsolute() {
		EmailProperties properties = propertiesWith("log", null, null, "/redefinir-senha");

		assertThatThrownBy(properties::validate)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("APP_PUBLIC_URL");
	}

	@Test
	void validate_failsFastWhenPublicUrlIsBlank() {
		EmailProperties properties = propertiesWith("log", null, null, "   ");

		assertThatThrownBy(properties::validate)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("APP_PUBLIC_URL");
	}

	@Test
	void validate_doesNotRequireCredentialsWhenProviderIsLog() {
		EmailProperties properties = propertiesWith("log", null, null, "https://sessaoadois.luisgosampaio.com");

		assertThatCode(properties::validate).doesNotThrowAnyException();
	}

	@Test
	void validate_succeedsWhenResendProviderHasCredentialsAndValidPublicUrl() {
		EmailProperties properties = propertiesWith("resend", "fake-api-key", "no-reply@sessaoadois.luisgosampaio.com",
			"https://sessaoadois.luisgosampaio.com");

		assertThatCode(properties::validate).doesNotThrowAnyException();
	}

	private EmailProperties propertiesWith(String provider, String apiKey, String from, String publicUrl) {
		EmailProperties properties = new EmailProperties();
		properties.setProvider(provider);
		properties.setApiKey(apiKey);
		properties.setFrom(from);
		properties.setPublicUrl(publicUrl);
		return properties;
	}
}
