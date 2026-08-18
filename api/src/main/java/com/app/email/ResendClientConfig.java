package com.app.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * RestClient dedicado ao provedor Resend - nao reaproveita o bean do TMDB. Timeouts
 * vem de EmailProperties (connect 3s / read 5s por default), mesmo padrao de
 * com.app.media.TmdbConfig.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.email", name = "provider", havingValue = "resend")
public class ResendClientConfig {

	private static final String RESEND_BASE_URL = "https://api.resend.com";

	@Bean
	RestClient resendRestClient(EmailProperties emailProperties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(emailProperties.getConnectTimeout());
		requestFactory.setReadTimeout(emailProperties.getReadTimeout());

		return RestClient.builder()
			.baseUrl(RESEND_BASE_URL)
			.requestFactory(requestFactory)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + emailProperties.getApiKey())
			.build();
	}
}
