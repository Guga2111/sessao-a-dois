package com.app.media;

import java.io.IOException;
import java.net.URI;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Garante que todo request ao TMDB carregue language=pt-BR, sem precisar ser
 * passado manualmente em cada chamada. Nao sobrescreve o parametro caso ele
 * ja tenha sido definido explicitamente na URI.
 */
class TmdbDefaultLanguageInterceptor implements ClientHttpRequestInterceptor {

	static final String DEFAULT_LANGUAGE = "pt-BR";

	@Override
	public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
			throws IOException {
		URI originalUri = request.getURI();
		if (UriComponentsBuilder.fromUri(originalUri).build().getQueryParams().containsKey("language")) {
			return execution.execute(request, body);
		}

		URI uriWithLanguage = UriComponentsBuilder.fromUri(originalUri)
			.queryParam("language", DEFAULT_LANGUAGE)
			.build(true)
			.toUri();

		HttpRequest wrappedRequest = new HttpRequestWrapper(request) {
			@Override
			public URI getURI() {
				return uriWithLanguage;
			}
		};
		return execution.execute(wrappedRequest, body);
	}
}
