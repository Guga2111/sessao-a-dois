package com.app.media;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponentsBuilder;

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

	@SuppressWarnings("unchecked")
	private static ArgumentCaptor<Function<UriBuilder, URI>> uriCaptor() {
		return ArgumentCaptor.forClass(Function.class);
	}

	@Test
	void discover_movieOnly_buildsDiscoverMovieUrlWithAllFilters() {
		RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
		ArgumentCaptor<Function<UriBuilder, URI>> captor = uriCaptor();
		when(restClient.get().uri(captor.capture()).retrieve().body(TmdbDiscoverResponse.class))
			.thenReturn(new TmdbDiscoverResponse(1, List.of(), 1, 0));

		MediaSearchService service = new MediaSearchService(restClient);
		DiscoverFilters filters = new DiscoverFilters(
			MediaType.MOVIE, "28,12", "2020,2000", null, 7.0, 9.5, 60, 180, DiscoverSortBy.VOTE_AVERAGE_ASC, 2);

		service.discover(filters);

		URI uri = captor.getValue().apply(UriComponentsBuilder.newInstance());
		var query = UriComponentsBuilder.fromUri(uri).build().getQueryParams();

		assertThat(uri.getPath()).isEqualTo("/discover/movie");
		assertThat(query.getFirst("page")).isEqualTo("2");
		assertThat(query.getFirst("sort_by")).isEqualTo("vote_average.asc");
		assertThat(query.getFirst("with_genres")).isEqualTo("28,12");
		assertThat(query.getFirst("vote_average.gte")).isEqualTo("7.0");
		assertThat(query.getFirst("vote_average.lte")).isEqualTo("9.5");
		assertThat(query.getFirst("with_runtime.gte")).isEqualTo("60");
		assertThat(query.getFirst("with_runtime.lte")).isEqualTo("180");
		assertThat(query.getFirst("primary_release_date.gte")).isEqualTo("2000-01-01");
		assertThat(query.getFirst("primary_release_date.lte")).isEqualTo("2029-12-31");
	}

	@Test
	void discover_movieOnly_withCertifications_usesHighestSelectedAsCeiling() {
		RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
		ArgumentCaptor<Function<UriBuilder, URI>> captor = uriCaptor();
		when(restClient.get().uri(captor.capture()).retrieve().body(TmdbDiscoverResponse.class))
			.thenReturn(new TmdbDiscoverResponse(1, List.of(), 1, 0));

		MediaSearchService service = new MediaSearchService(restClient);
		DiscoverFilters filters = new DiscoverFilters(
			MediaType.MOVIE, null, null, "Livre,12,16", null, null, null, null, DiscoverSortBy.POPULARITY_DESC, 1);

		service.discover(filters);

		URI uri = captor.getValue().apply(UriComponentsBuilder.newInstance());
		var query = UriComponentsBuilder.fromUri(uri).build().getQueryParams();

		assertThat(query.getFirst("certification_country")).isEqualTo("BR");
		assertThat(query.getFirst("certification.lte")).isEqualTo("16");
	}

	@Test
	void discover_movieOnly_withOnlyLivreChip_usesLAsCeiling() {
		RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
		ArgumentCaptor<Function<UriBuilder, URI>> captor = uriCaptor();
		when(restClient.get().uri(captor.capture()).retrieve().body(TmdbDiscoverResponse.class))
			.thenReturn(new TmdbDiscoverResponse(1, List.of(), 1, 0));

		MediaSearchService service = new MediaSearchService(restClient);
		DiscoverFilters filters = new DiscoverFilters(
			MediaType.MOVIE, null, null, "Livre", null, null, null, null, DiscoverSortBy.POPULARITY_DESC, 1);

		service.discover(filters);

		URI uri = captor.getValue().apply(UriComponentsBuilder.newInstance());
		var query = UriComponentsBuilder.fromUri(uri).build().getQueryParams();

		assertThat(query.getFirst("certification_country")).isEqualTo("BR");
		assertThat(query.getFirst("certification.lte")).isEqualTo("L");
	}

	@Test
	void discover_movieOnly_withoutCertifications_omitsCertificationParams() {
		RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
		ArgumentCaptor<Function<UriBuilder, URI>> captor = uriCaptor();
		when(restClient.get().uri(captor.capture()).retrieve().body(TmdbDiscoverResponse.class))
			.thenReturn(new TmdbDiscoverResponse(1, List.of(), 1, 0));

		MediaSearchService service = new MediaSearchService(restClient);
		DiscoverFilters filters = new DiscoverFilters(
			MediaType.MOVIE, null, null, null, null, null, null, null, DiscoverSortBy.POPULARITY_DESC, 1);

		service.discover(filters);

		URI uri = captor.getValue().apply(UriComponentsBuilder.newInstance());
		var query = UriComponentsBuilder.fromUri(uri).build().getQueryParams();

		assertThat(query.containsKey("certification_country")).isFalse();
		assertThat(query.containsKey("certification.lte")).isFalse();
	}

	@Test
	void discover_tvOnly_ignoresCertificationsEvenWhenProvided() {
		RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
		ArgumentCaptor<Function<UriBuilder, URI>> captor = uriCaptor();
		when(restClient.get().uri(captor.capture()).retrieve().body(TmdbDiscoverResponse.class))
			.thenReturn(new TmdbDiscoverResponse(1, List.of(), 1, 0));

		MediaSearchService service = new MediaSearchService(restClient);
		DiscoverFilters filters = new DiscoverFilters(
			MediaType.TV, null, null, "Livre,18", null, null, null, null, DiscoverSortBy.POPULARITY_DESC, 1);

		service.discover(filters);

		URI uri = captor.getValue().apply(UriComponentsBuilder.newInstance());
		var query = UriComponentsBuilder.fromUri(uri).build().getQueryParams();

		assertThat(uri.getPath()).isEqualTo("/discover/tv");
		assertThat(query.containsKey("certification_country")).isFalse();
		assertThat(query.containsKey("certification.lte")).isFalse();
	}

	@Test
	void discover_tvOnly_usesFirstAirDateForDecadesAndReleaseDateSort() {
		RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
		ArgumentCaptor<Function<UriBuilder, URI>> captor = uriCaptor();
		when(restClient.get().uri(captor.capture()).retrieve().body(TmdbDiscoverResponse.class))
			.thenReturn(new TmdbDiscoverResponse(1, List.of(), 1, 0));

		MediaSearchService service = new MediaSearchService(restClient);
		DiscoverFilters filters = new DiscoverFilters(
			MediaType.TV, null, "1990", null, null, null, null, null, DiscoverSortBy.RELEASE_DATE_DESC, 1);

		service.discover(filters);

		URI uri = captor.getValue().apply(UriComponentsBuilder.newInstance());
		var query = UriComponentsBuilder.fromUri(uri).build().getQueryParams();

		assertThat(uri.getPath()).isEqualTo("/discover/tv");
		assertThat(query.getFirst("sort_by")).isEqualTo("first_air_date.desc");
		assertThat(query.getFirst("first_air_date.gte")).isEqualTo("1990-01-01");
		assertThat(query.getFirst("first_air_date.lte")).isEqualTo("1999-12-31");
		assertThat(query.containsKey("with_genres")).isFalse();
	}

	@Test
	void discover_withoutMediaType_callsBothEndpointsMergesAndSlicesTo20() {
		RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);

		TmdbDiscoverItem highPopMovie = new TmdbDiscoverItem(
			1, "Filme Popular", null, "2020-01-01", null, "/a.jpg", "overview", 8.0, 500.0);
		TmdbDiscoverItem lowPopTv = new TmdbDiscoverItem(
			2, null, "Serie Pouco Popular", null, "2020-01-01", "/b.jpg", "overview", 7.0, 10.0);
		TmdbDiscoverResponse movieResponse = new TmdbDiscoverResponse(1, List.of(highPopMovie), 5, 90);
		TmdbDiscoverResponse tvResponse = new TmdbDiscoverResponse(1, List.of(lowPopTv), 3, 50);

		when(restClient.get().uri(any(Function.class)).retrieve().body(TmdbDiscoverResponse.class))
			.thenReturn(movieResponse, tvResponse);

		MediaSearchService service = new MediaSearchService(restClient);
		DiscoverFilters filters = new DiscoverFilters(
			null, null, null, null, null, null, null, null, DiscoverSortBy.POPULARITY_DESC, 1);

		MediaPage page = service.discover(filters);

		assertThat(page.results()).hasSize(2);
		assertThat(page.results().get(0).tmdbId()).isEqualTo(1);
		assertThat(page.results().get(0).mediaType()).isEqualTo(MediaType.MOVIE);
		assertThat(page.results().get(1).tmdbId()).isEqualTo(2);
		assertThat(page.results().get(1).mediaType()).isEqualTo(MediaType.TV);
		assertThat(page.totalResults()).isEqualTo(140);
		assertThat(page.totalPages()).isEqualTo(7);
	}

	@Test
	void discover_withoutMediaType_limitsMergedResultsToTwentyItems() {
		RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);

		List<TmdbDiscoverItem> movies = new java.util.ArrayList<>();
		for (int i = 0; i < 20; i++) {
			movies.add(new TmdbDiscoverItem(i, "Filme " + i, null, "2020-01-01", null, null, null, 5.0, 100.0 - i));
		}
		List<TmdbDiscoverItem> tvShows = new java.util.ArrayList<>();
		for (int i = 0; i < 20; i++) {
			tvShows.add(new TmdbDiscoverItem(100 + i, null, "Serie " + i, null, "2020-01-01", null, null, 5.0, 50.0 - i));
		}

		when(restClient.get().uri(any(Function.class)).retrieve().body(TmdbDiscoverResponse.class))
			.thenReturn(new TmdbDiscoverResponse(1, movies, 1, 20), new TmdbDiscoverResponse(1, tvShows, 1, 20));

		MediaSearchService service = new MediaSearchService(restClient);
		DiscoverFilters filters = new DiscoverFilters(
			null, null, null, null, null, null, null, null, DiscoverSortBy.POPULARITY_DESC, 1);

		MediaPage page = service.discover(filters);

		assertThat(page.results()).hasSize(20);
		assertThat(page.results().get(0).tmdbId()).isEqualTo(0);
	}

	@Test
	void discover_throwsTmdbUnavailableWhenTmdbIsDown() {
		RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
		HttpServerErrorException serverError = HttpServerErrorException.create(
			HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);

		when(restClient.get().uri(any(Function.class)).retrieve().body(TmdbDiscoverResponse.class))
			.thenThrow(serverError);

		MediaSearchService service = new MediaSearchService(restClient);
		DiscoverFilters filters = new DiscoverFilters(
			MediaType.MOVIE, null, null, null, null, null, null, null, DiscoverSortBy.POPULARITY_DESC, 1);

		assertThatThrownBy(() -> service.discover(filters))
			.isInstanceOf(TmdbUnavailableException.class)
			.hasCauseInstanceOf(HttpServerErrorException.class);
	}
}
