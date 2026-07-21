package com.app.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * RestClient central para a API do TMDB. A API Key nunca e hardcoded: vem de
 * TMDB_API_KEY (env var / application.properties). Se ausente, a aplicacao
 * falha ao subir (fail-fast) para evitar chamadas anonimas ao TMDB.
 */
@Configuration
public class TmdbConfig {

	private final String apiKey;

	private final String baseUrl;

	public TmdbConfig(
			@Value("${tmdb.api-key:}") String apiKey,
			@Value("${tmdb.base-url:https://api.themoviedb.org/3}") String baseUrl) {
		this.apiKey = apiKey;
		this.baseUrl = baseUrl;
	}

	@Bean
	RestClient tmdbRestClient() {
		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalStateException(
				"TMDB_API_KEY nao configurada. Defina a variavel de ambiente TMDB_API_KEY antes de iniciar a aplicacao.");
		}

		return RestClient.builder()
			.baseUrl(baseUrl)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
			.requestInterceptor(new TmdbDefaultLanguageInterceptor())
			.build();
	}
}
