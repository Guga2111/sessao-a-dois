package com.app.media;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MediaSearchServiceTest {

	@Test
	void search_returnsMixedMovieAndTvResultsFilteringOutOtherTypes() {
		RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
		TmdbMultiSearchItem movie = new TmdbMultiSearchItem(
			603, "movie", "Matrix", null, "1999-03-30", null, "/poster-matrix.jpg", "Um hacker descobre a verdade.", 8.2);
		TmdbMultiSearchItem tv = new TmdbMultiSearchItem(
			1668, "tv", null, "Friends", null, "1994-09-22", "/poster-friends.jpg", "Seis amigos em Nova York.", 8.4);
		TmdbMultiSearchItem person = new TmdbMultiSearchItem(
			999, "person", "Keanu Reeves", null, null, null, "/keanu.jpg", null, null);
		TmdbMultiSearchResponse response = new TmdbMultiSearchResponse(List.of(movie, tv, person));

		when(restClient.get().uri(any(Function.class)).retrieve().body(TmdbMultiSearchResponse.class))
			.thenReturn(response);

		MediaSearchService service = new MediaSearchService(restClient);
		List<MediaSearchResult> results = service.search("matrix");

		assertThat(results).hasSize(2);

		MediaSearchResult movieResult = results.get(0);
		assertThat(movieResult.tmdbId()).isEqualTo(603);
		assertThat(movieResult.mediaType()).isEqualTo(MediaType.MOVIE);
		assertThat(movieResult.title()).isEqualTo("Matrix");
		assertThat(movieResult.year()).isEqualTo(1999);
		assertThat(movieResult.posterUrl()).isEqualTo("https://image.tmdb.org/t/p/w500/poster-matrix.jpg");
		assertThat(movieResult.overview()).isEqualTo("Um hacker descobre a verdade.");
		assertThat(movieResult.voteAverage()).isEqualTo(8.2);

		MediaSearchResult tvResult = results.get(1);
		assertThat(tvResult.tmdbId()).isEqualTo(1668);
		assertThat(tvResult.mediaType()).isEqualTo(MediaType.TV);
		assertThat(tvResult.title()).isEqualTo("Friends");
		assertThat(tvResult.year()).isEqualTo(1994);
		assertThat(tvResult.posterUrl()).isEqualTo("https://image.tmdb.org/t/p/w500/poster-friends.jpg");
	}

	@Test
	void search_returnsEmptyListWhenTmdbHasNoResults() {
		RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
		when(restClient.get().uri(any(Function.class)).retrieve().body(TmdbMultiSearchResponse.class))
			.thenReturn(new TmdbMultiSearchResponse(List.of()));

		MediaSearchService service = new MediaSearchService(restClient);

		assertThat(service.search("titulo-inexistente")).isEmpty();
	}

	@Test
	void search_returnsEmptyListWhenTmdbResponseBodyIsNull() {
		RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
		when(restClient.get().uri(any(Function.class)).retrieve().body(TmdbMultiSearchResponse.class))
			.thenReturn(null);

		MediaSearchService service = new MediaSearchService(restClient);

		assertThat(service.search("qualquer-coisa")).isEmpty();
	}

	@Test
	void search_handlesMissingPosterAndReleaseDateGracefully() {
		RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
		TmdbMultiSearchItem movieWithoutExtras = new TmdbMultiSearchItem(
			1, "movie", "Sem Poster", null, null, null, null, "sem overview", null);
		when(restClient.get().uri(any(Function.class)).retrieve().body(TmdbMultiSearchResponse.class))
			.thenReturn(new TmdbMultiSearchResponse(List.of(movieWithoutExtras)));

		MediaSearchService service = new MediaSearchService(restClient);
		List<MediaSearchResult> results = service.search("sem poster");

		assertThat(results).hasSize(1);
		assertThat(results.get(0).posterUrl()).isNull();
		assertThat(results.get(0).year()).isNull();
	}

	@Test
	void search_throwsTmdbUnavailableWhenTmdbReturnsServerError() {
		RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
		HttpServerErrorException serverError = HttpServerErrorException.create(
			HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);

		when(restClient.get().uri(any(Function.class)).retrieve().body(TmdbMultiSearchResponse.class))
			.thenThrow(serverError);

		MediaSearchService service = new MediaSearchService(restClient);

		assertThatThrownBy(() -> service.search("matrix"))
			.isInstanceOf(TmdbUnavailableException.class)
			.hasCauseInstanceOf(HttpServerErrorException.class);
	}

	@Test
	void search_throwsTmdbUnavailableWhenTmdbTimesOut() {
		RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
		ResourceAccessException timeout = new ResourceAccessException("timeout");

		when(restClient.get().uri(any(Function.class)).retrieve().body(TmdbMultiSearchResponse.class))
			.thenThrow(timeout);

		MediaSearchService service = new MediaSearchService(restClient);

		assertThatThrownBy(() -> service.search("matrix"))
			.isInstanceOf(TmdbUnavailableException.class)
			.hasCause(timeout);
	}
}
