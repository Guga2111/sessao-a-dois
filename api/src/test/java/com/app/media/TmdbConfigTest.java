package com.app.media;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TmdbConfigTest {

	private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(3);

	private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(5);

	@Test
	void tmdbRestClient_failsFastWhenApiKeyIsMissing() {
		TmdbConfig config = new TmdbConfig("", "https://api.themoviedb.org/3", DEFAULT_CONNECT_TIMEOUT,
			DEFAULT_READ_TIMEOUT);

		assertThatThrownBy(config::tmdbRestClient)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("TMDB_API_KEY");
	}

	@Test
	void tmdbRestClient_failsFastWhenApiKeyIsBlank() {
		TmdbConfig config = new TmdbConfig("   ", "https://api.themoviedb.org/3", DEFAULT_CONNECT_TIMEOUT,
			DEFAULT_READ_TIMEOUT);

		assertThatThrownBy(config::tmdbRestClient)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("TMDB_API_KEY");
	}

	@Test
	void tmdbRestClient_buildsSuccessfullyWhenApiKeyIsPresent() {
		TmdbConfig config = new TmdbConfig("fake-api-key", "https://api.themoviedb.org/3", DEFAULT_CONNECT_TIMEOUT,
			DEFAULT_READ_TIMEOUT);

		RestClient restClient = config.tmdbRestClient();

		assertThat(restClient).isNotNull();
	}

	@Test
	void tmdbRestClient_appliesDefaultTimeoutsWhenNoPropertyIsSet() {
		TmdbConfig config = new TmdbConfig("fake-api-key", "https://api.themoviedb.org/3", DEFAULT_CONNECT_TIMEOUT,
			DEFAULT_READ_TIMEOUT);

		SimpleClientHttpRequestFactory requestFactory = requestFactoryOf(config);

		assertThat(ReflectionTestUtils.getField(requestFactory, "connectTimeout")).isEqualTo(3000);
		assertThat(ReflectionTestUtils.getField(requestFactory, "readTimeout")).isEqualTo(5000);
	}

	@Test
	void tmdbRestClient_appliesCustomTimeoutsWhenPropertiesAreSet() {
		TmdbConfig config = new TmdbConfig("fake-api-key", "https://api.themoviedb.org/3", Duration.ofSeconds(1),
			Duration.ofSeconds(2));

		SimpleClientHttpRequestFactory requestFactory = requestFactoryOf(config);

		assertThat(ReflectionTestUtils.getField(requestFactory, "connectTimeout")).isEqualTo(1000);
		assertThat(ReflectionTestUtils.getField(requestFactory, "readTimeout")).isEqualTo(2000);
	}

	private SimpleClientHttpRequestFactory requestFactoryOf(TmdbConfig config) {
		RestClient restClient = config.tmdbRestClient();
		Object requestFactory = ReflectionTestUtils.getField(restClient, "clientRequestFactory");
		assertThat(requestFactory).isInstanceOf(SimpleClientHttpRequestFactory.class);
		return (SimpleClientHttpRequestFactory) requestFactory;
	}
}
