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
		TmdbMultiSearchResponse response = new TmdbMultiSearchResponse(1, List.of(movie, tv, person), 10, 190);

		when(restClient.get().uri(any(Function.class)).retrieve().body(TmdbMultiSearchResponse.class))
			.thenReturn(response);

		MediaSearchService service = new MediaSearchService(restClient);
		MediaPage page = service.search("matrix", 1);
		List<MediaSearchResult> results = page.results();

		assertThat(results).hasSize(2);
		assertThat(page.page()).isEqualTo(1);
		assertThat(page.totalResults()).isEqualTo(190);
		assertThat(page.totalPages()).isEqualTo(10);

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
	void search_returnsEmptyPageWhenTmdbHasNoResults() {
		RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
		when(restClient.get().uri(any(Function.class)).retrieve().body(TmdbMultiSearchResponse.class))
			.thenReturn(new TmdbMultiSearchResponse(1, List.of(), 0, 0));

		MediaSearchService service = new MediaSearchService(restClient);

		assertThat(service.search("titulo-inexistente", 1).results()).isEmpty();
	}

	@Test
	void search_returnsEmptyPageWhenTmdbResponseBodyIsNull() {
		RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
		when(restClient.get().uri(any(Function.class)).retrieve().body(TmdbMultiSearchResponse.class))
			.thenReturn(null);

		MediaSearchService service = new MediaSearchService(restClient);

		MediaPage page = service.search("qualquer-coisa", 1);
		assertThat(page.results()).isEmpty();
		assertThat(page.page()).isEqualTo(1);
		assertThat(page.totalResults()).isZero();
		assertThat(page.totalPages()).isZero();
	}

	@Test
	void search_returnsEmptyPageWhenResultsFieldIsNull() {
		RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
		when(restClient.get().uri(any(Function.class)).retrieve().body(TmdbMultiSearchResponse.class))
			.thenReturn(new TmdbMultiSearchResponse(1, null, 0, 0));

		MediaSearchService service = new MediaSearchService(restClient);

		assertThat(service.search("resposta-sem-results", 1).results()).isEmpty();
	}

	@Test
	void search_handlesMissingPosterAndReleaseDateGracefully() {
		RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
		TmdbMultiSearchItem movieWithoutExtras = new TmdbMultiSearchItem(
			1, "movie", "Sem Poster", null, null, null, null, "sem overview", null);
		when(restClient.get().uri(any(Function.class)).retrieve().body(TmdbMultiSearchResponse.class))
			.thenReturn(new TmdbMultiSearchResponse(1, List.of(movieWithoutExtras), 1, 1));

		MediaSearchService service = new MediaSearchService(restClient);
		List<MediaSearchResult> results = service.search("sem poster", 1).results();

		assertThat(results).hasSize(1);
		assertThat(results.get(0).posterUrl()).isNull();
		assertThat(results.get(0).year()).isNull();
	}

	@Test
	void search_clampsTotalsToTmdbCapWhenTmdbReportsMore() {
		RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
		TmdbMultiSearchResponse response = new TmdbMultiSearchResponse(1, List.of(), 100_000, 999_999);

		when(restClient.get().uri(any(Function.class)).retrieve().body(TmdbMultiSearchResponse.class))
			.thenReturn(response);

		MediaSearchService service = new MediaSearchService(restClient);
		MediaPage page = service.search("matrix", 1);

		assertThat(page.totalPages()).isEqualTo(500);
		assertThat(page.totalResults()).isEqualTo(10_000);
	}

	@Test
	void search_throwsTmdbUnavailableWhenTmdbReturnsServerError() {
		RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
		HttpServerErrorException serverError = HttpServerErrorException.create(
			HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);

		when(restClient.get().uri(any(Function.class)).retrieve().body(TmdbMultiSearchResponse.class))
			.thenThrow(serverError);

		MediaSearchService service = new MediaSearchService(restClient);

		assertThatThrownBy(() -> service.search("matrix", 1))
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

		assertThatThrownBy(() -> service.search("matrix", 1))
			.isInstanceOf(TmdbUnavailableException.class)
			.hasCause(timeout);
	}

	@Test
	void trending_returnsMixedMovieAndTvResultsFilteringOutPeople() {
		RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
		TmdbMultiSearchItem movie = new TmdbMultiSearchItem(
			603, "movie", "Matrix", null, "1999-03-30", null, "/poster-matrix.jpg", "Um hacker descobre a verdade.", 8.2);
		TmdbMultiSearchItem tv = new TmdbMultiSearchItem(
			1668, "tv", null, "Friends", null, "1994-09-22", "/poster-friends.jpg", "Seis amigos em Nova York.", 8.4);
		TmdbMultiSearchItem person = new TmdbMultiSearchItem(
			999, "person", "Keanu Reeves", null, null, null, "/keanu.jpg", null, null);
		TmdbTrendingResponse response = new TmdbTrendingResponse(1, List.of(movie, tv, person), 50, 1000);

		when(restClient.get().uri(any(Function.class)).retrieve().body(TmdbTrendingResponse.class))
			.thenReturn(response);

		MediaSearchService service = new MediaSearchService(restClient);
		MediaPage page = service.trending(1);

		assertThat(page.results()).hasSize(2);
		assertThat(page.page()).isEqualTo(1);
		assertThat(page.totalResults()).isEqualTo(1000);
		assertThat(page.totalPages()).isEqualTo(50);

		MediaSearchResult movieResult = page.results().get(0);
		assertThat(movieResult.tmdbId()).isEqualTo(603);
		assertThat(movieResult.mediaType()).isEqualTo(MediaType.MOVIE);
		assertThat(movieResult.title()).isEqualTo("Matrix");
	}

	@Test
	void trending_returnsEmptyPageWhenTmdbResponseBodyIsNull() {
		RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
		when(restClient.get().uri(any(Function.class)).retrieve().body(TmdbTrendingResponse.class))
			.thenReturn(null);

		MediaSearchService service = new MediaSearchService(restClient);
		MediaPage page = service.trending(1);

		assertThat(page.results()).isEmpty();
		assertThat(page.page()).isEqualTo(1);
		assertThat(page.totalResults()).isZero();
		assertThat(page.totalPages()).isZero();
	}

	@Test
	void trending_clampsTotalsToTmdbCapWhenTmdbReportsMore() {
		RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
		TmdbTrendingResponse response = new TmdbTrendingResponse(1, List.of(), 100_000, 999_999);

		when(restClient.get().uri(any(Function.class)).retrieve().body(TmdbTrendingResponse.class))
			.thenReturn(response);

		MediaSearchService service = new MediaSearchService(restClient);
		MediaPage page = service.trending(1);

		assertThat(page.totalPages()).isEqualTo(500);
		assertThat(page.totalResults()).isEqualTo(10_000);
	}

	@Test
	void trending_throwsTmdbUnavailableWhenTmdbIsDown() {
		RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
		HttpServerErrorException serverError = HttpServerErrorException.create(
			HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);

		when(restClient.get().uri(any(Function.class)).retrieve().body(TmdbTrendingResponse.class))
			.thenThrow(serverError);

		MediaSearchService service = new MediaSearchService(restClient);

		assertThatThrownBy(() -> service.trending(1))
			.isInstanceOf(TmdbUnavailableException.class)
			.hasCauseInstanceOf(HttpServerErrorException.class);
	}
}
