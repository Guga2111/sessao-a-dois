package com.app.media;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TmdbDefaultLanguageInterceptorTest {

	@Mock
	private HttpRequest request;

	@Mock
	private ClientHttpRequestExecution execution;

	@Mock
	private ClientHttpResponse response;

	private final TmdbDefaultLanguageInterceptor interceptor = new TmdbDefaultLanguageInterceptor();

	@Test
	void addsDefaultLanguageWhenAbsent() throws Exception {
		when(request.getURI()).thenReturn(URI.create("https://api.themoviedb.org/3/search/multi?query=matrix"));
		when(execution.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
			.thenReturn(response);

		interceptor.intercept(request, new byte[0], execution);

		ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
		org.mockito.Mockito.verify(execution).execute(captor.capture(), org.mockito.ArgumentMatchers.any());

		URI calledUri = captor.getValue().getURI();
		assertThat(calledUri.getQuery()).contains("language=pt-BR");
		assertThat(calledUri.getQuery()).contains("query=matrix");
	}

	@Test
	void doesNotOverrideLanguageWhenAlreadyPresent() throws Exception {
		when(request.getURI()).thenReturn(URI.create("https://api.themoviedb.org/3/search/multi?language=en-US"));
		when(execution.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
			.thenReturn(response);

		interceptor.intercept(request, new byte[0], execution);

		ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
		org.mockito.Mockito.verify(execution).execute(captor.capture(), org.mockito.ArgumentMatchers.any());

		assertThat(captor.getValue()).isSameAs(request);
	}
}
