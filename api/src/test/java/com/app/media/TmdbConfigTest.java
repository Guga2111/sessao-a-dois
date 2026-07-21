package com.app.media;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TmdbConfigTest {

	@Test
	void tmdbRestClient_failsFastWhenApiKeyIsMissing() {
		TmdbConfig config = new TmdbConfig("", "https://api.themoviedb.org/3");

		assertThatThrownBy(config::tmdbRestClient)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("TMDB_API_KEY");
	}

	@Test
	void tmdbRestClient_failsFastWhenApiKeyIsBlank() {
		TmdbConfig config = new TmdbConfig("   ", "https://api.themoviedb.org/3");

		assertThatThrownBy(config::tmdbRestClient)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("TMDB_API_KEY");
	}

	@Test
	void tmdbRestClient_buildsSuccessfullyWhenApiKeyIsPresent() {
		TmdbConfig config = new TmdbConfig("fake-api-key", "https://api.themoviedb.org/3");

		RestClient restClient = config.tmdbRestClient();

		assertThat(restClient).isNotNull();
	}
}
