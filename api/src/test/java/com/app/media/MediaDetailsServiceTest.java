package com.app.media;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MediaDetailsServiceTest {

	@Test
	void getDetails_returnsMovieDetailsWithWatchProviders() {
		RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
		TmdbGenre action = new TmdbGenre(28, "Acao");
		TmdbWatchProviderEntry netflix = new TmdbWatchProviderEntry("Netflix", "/netflix.jpg");
		TmdbWatchProviderCountry brazil = new TmdbWatchProviderCountry(List.of(netflix));
		TmdbWatchProvidersResponse watchProviders = new TmdbWatchProvidersResponse(Map.of("BR", brazil));
		TmdbMediaDetailsResponse response = new TmdbMediaDetailsResponse(
			603, "Matrix", null, "1999-03-30", null, "/poster-matrix.jpg", "Um hacker descobre a verdade.",
			List.of(action), 8.2, 136, watchProviders);

		when(restClient.get().uri(any(Function.class)).retrieve().body(TmdbMediaDetailsResponse.class))
			.thenReturn(response);

		MediaDetailsService service = new MediaDetailsService(restClient);
		MediaDetails details = service.getDetails(MediaType.MOVIE, 603);

		assertThat(details.tmdbId()).isEqualTo(603);
		assertThat(details.mediaType()).isEqualTo(MediaType.MOVIE);
		assertThat(details.title()).isEqualTo("Matrix");
		assertThat(details.year()).isEqualTo(1999);
		assertThat(details.posterUrl()).isEqualTo("https://image.tmdb.org/t/p/w500/poster-matrix.jpg");
		assertThat(details.overview()).isEqualTo("Um hacker descobre a verdade.");
		assertThat(details.genres()).containsExactly("Acao");
		assertThat(details.genreIds()).containsExactly(28);
		assertThat(details.voteAverage()).isEqualTo(8.2);
		assertThat(details.runtime()).isEqualTo(136);
		assertThat(details.watchProviders()).hasSize(1);
		assertThat(details.watchProviders().get(0).name()).isEqualTo("Netflix");
		assertThat(details.watchProviders().get(0).logoUrl()).isEqualTo("https://image.tmdb.org/t/p/w92/netflix.jpg");
	}

	@Test
	void getDetails_returnsTvDetailsWithoutRuntimeAndEmptyWatchProvidersWhenBrazilHasNoData() {
		RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
		TmdbGenre drama = new TmdbGenre(18, "Drama");
		TmdbMediaDetailsResponse response = new TmdbMediaDetailsResponse(
			1668, null, "Friends", null, "1994-09-22", "/poster-friends.jpg", "Seis amigos em Nova York.",
			List.of(drama), 8.4, null, new TmdbWatchProvidersResponse(Map.of()));

		when(restClient.get().uri(any(Function.class)).retrieve().body(TmdbMediaDetailsResponse.class))
			.thenReturn(response);

		MediaDetailsService service = new MediaDetailsService(restClient);
		MediaDetails details = service.getDetails(MediaType.TV, 1668);

		assertThat(details.mediaType()).isEqualTo(MediaType.TV);
		assertThat(details.title()).isEqualTo("Friends");
		assertThat(details.year()).isEqualTo(1994);
		assertThat(details.runtime()).isNull();
		assertThat(details.watchProviders()).isEmpty();
	}

	@Test
	void getDetails_throwsNotFoundWhenTmdbIdDoesNotExist() {
		RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
		HttpClientErrorException notFound = HttpClientErrorException.create(
			HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);

		when(restClient.get().uri(any(Function.class)).retrieve().body(TmdbMediaDetailsResponse.class))
			.thenThrow(notFound);

		MediaDetailsService service = new MediaDetailsService(restClient);

		assertThatThrownBy(() -> service.getDetails(MediaType.MOVIE, 999999999))
			.isInstanceOf(MediaNotFoundException.class);
	}

	@Test
	void getDetails_throwsTmdbUnavailableWhenTmdbReturnsServerError() {
		RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
		HttpServerErrorException serverError = HttpServerErrorException.create(
			HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);

		when(restClient.get().uri(any(Function.class)).retrieve().body(TmdbMediaDetailsResponse.class))
			.thenThrow(serverError);

		MediaDetailsService service = new MediaDetailsService(restClient);

		assertThatThrownBy(() -> service.getDetails(MediaType.MOVIE, 603))
			.isInstanceOf(TmdbUnavailableException.class)
			.hasCauseInstanceOf(HttpServerErrorException.class);
	}

	@Test
	void getDetails_throwsTmdbUnavailableWhenTmdbTimesOut() {
		RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
		ResourceAccessException timeout = new ResourceAccessException("timeout");

		when(restClient.get().uri(any(Function.class)).retrieve().body(TmdbMediaDetailsResponse.class))
			.thenThrow(timeout);

		MediaDetailsService service = new MediaDetailsService(restClient);

		assertThatThrownBy(() -> service.getDetails(MediaType.MOVIE, 603))
			.isInstanceOf(TmdbUnavailableException.class)
			.hasCause(timeout);
	}
}
