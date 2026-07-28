package com.app.media;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class MediaSearchService {

	private static final String POSTER_BASE_URL = "https://image.tmdb.org/t/p/w500";

	private static final String SEARCH_ENDPOINT = "/search/multi";

	private static final String TRENDING_ENDPOINT = "/trending/all/week";

	private static final String DISCOVER_MOVIE_ENDPOINT = "/discover/movie";

	private static final String DISCOVER_TV_ENDPOINT = "/discover/tv";

	private static final String GENRE_MOVIE_ENDPOINT = "/genre/movie/list";

	private static final String GENRE_TV_ENDPOINT = "/genre/tv/list";

	private static final int PAGE_SIZE = 20;

	/**
	 * Chips de classificacao indicativa BR, do mais permissivo ao mais restritivo.
	 * TMDB usa exatamente estes codigos como certification_country=BR.
	 */
	private static final List<String> CERTIFICATION_ORDER = List.of("L", "10", "12", "14", "16", "18");

	private final RestClient tmdbRestClient;

	public MediaSearchService(RestClient tmdbRestClient) {
		this.tmdbRestClient = tmdbRestClient;
	}

	public MediaPage search(String query, int page) {
		TmdbMultiSearchResponse response;
		try {
			response = tmdbRestClient.get()
				.uri(uriBuilder -> uriBuilder.path(SEARCH_ENDPOINT)
					.queryParam("query", query)
					.queryParam("page", page)
					.build())
				.retrieve()
				.body(TmdbMultiSearchResponse.class);
		}
		catch (RestClientResponseException | ResourceAccessException ex) {
			throw new TmdbUnavailableException(SEARCH_ENDPOINT, ex);
		}

		if (response == null || response.results() == null) {
			return new MediaPage(List.of(), page, 0, 0);
		}

		List<MediaSearchResult> results = response.results().stream()
			.filter(item -> "movie".equals(item.mediaType()) || "tv".equals(item.mediaType()))
			.map(this::toMediaSearchResult)
			.toList();

		return new MediaPage(results, response.page(), response.totalResults(), response.totalPages());
	}

	public MediaPage trending(int page) {
		TmdbTrendingResponse response;
		try {
			response = tmdbRestClient.get()
				.uri(uriBuilder -> uriBuilder.path(TRENDING_ENDPOINT).queryParam("page", page).build())
				.retrieve()
				.body(TmdbTrendingResponse.class);
		}
		catch (RestClientResponseException | ResourceAccessException ex) {
			throw new TmdbUnavailableException(TRENDING_ENDPOINT, ex);
		}

		if (response == null || response.results() == null) {
			return new MediaPage(List.of(), page, 0, 0);
		}

		List<MediaSearchResult> results = response.results().stream()
			.filter(item -> "movie".equals(item.mediaType()) || "tv".equals(item.mediaType()))
			.map(this::toMediaSearchResult)
			.toList();

		return new MediaPage(results, response.page(), response.totalResults(), response.totalPages());
	}

	public MediaPage discover(DiscoverFilters filters) {
		DateRange dateRange = dateRangeFromDecades(filters.releaseDecades());

		if (filters.mediaType() == MediaType.MOVIE) {
			return discoverSingle(MediaType.MOVIE, filters, dateRange);
		}
		if (filters.mediaType() == MediaType.TV) {
			return discoverSingle(MediaType.TV, filters, dateRange);
		}
		return discoverBoth(filters, dateRange);
	}

	private MediaPage discoverSingle(MediaType mediaType, DiscoverFilters filters, DateRange dateRange) {
		TmdbDiscoverResponse response = callDiscover(mediaType, filters, dateRange);

		if (response == null || response.results() == null) {
			return new MediaPage(List.of(), filters.page(), 0, 0);
		}

		List<MediaSearchResult> results = response.results().stream()
			.map(item -> toMediaSearchResult(item, mediaType))
			.toList();

		return new MediaPage(results, response.page(), response.totalResults(), response.totalPages());
	}

	private MediaPage discoverBoth(DiscoverFilters filters, DateRange dateRange) {
		TmdbDiscoverResponse movieResponse = callDiscover(MediaType.MOVIE, filters, dateRange);
		TmdbDiscoverResponse tvResponse = callDiscover(MediaType.TV, filters, dateRange);

		List<ScoredItem> combined = new ArrayList<>();
		if (movieResponse != null && movieResponse.results() != null) {
			movieResponse.results().forEach(item -> combined.add(new ScoredItem(item, MediaType.MOVIE)));
		}
		if (tvResponse != null && tvResponse.results() != null) {
			tvResponse.results().forEach(item -> combined.add(new ScoredItem(item, MediaType.TV)));
		}

		combined.sort(comparatorFor(filters.sortBy()));

		List<MediaSearchResult> results = combined.stream()
			.limit(PAGE_SIZE)
			.map(scored -> toMediaSearchResult(scored.item(), scored.mediaType()))
			.toList();

		int movieTotal = movieResponse != null ? movieResponse.totalResults() : 0;
		int tvTotal = tvResponse != null ? tvResponse.totalResults() : 0;
		int totalResults = movieTotal + tvTotal;
		int totalPages = (int) Math.ceil(totalResults / (double) PAGE_SIZE);

		return new MediaPage(results, filters.page(), totalResults, totalPages);
	}

	/**
	 * Generos de filme e serie do TMDB (pt-BR), mesclados e deduplicados por nome
	 * (mantendo a primeira ocorrencia, filmes antes de series).
	 */
	public List<MediaGenre> genres() {
		List<TmdbGenre> movieGenres = fetchGenreList(GENRE_MOVIE_ENDPOINT);
		List<TmdbGenre> tvGenres = fetchGenreList(GENRE_TV_ENDPOINT);

		LinkedHashMap<String, TmdbGenre> byName = new LinkedHashMap<>();
		movieGenres.forEach(genre -> byName.putIfAbsent(genre.name(), genre));
		tvGenres.forEach(genre -> byName.putIfAbsent(genre.name(), genre));

		return byName.values().stream().map(genre -> new MediaGenre(genre.id(), genre.name())).toList();
	}

	private List<TmdbGenre> fetchGenreList(String endpoint) {
		TmdbGenreListResponse response;
		try {
			response = tmdbRestClient.get().uri(endpoint).retrieve().body(TmdbGenreListResponse.class);
		}
		catch (RestClientResponseException | ResourceAccessException ex) {
			throw new TmdbUnavailableException(endpoint, ex);
		}

		return response == null || response.genres() == null ? List.of() : response.genres();
	}

	private TmdbDiscoverResponse callDiscover(MediaType mediaType, DiscoverFilters filters, DateRange dateRange) {
		String endpoint = mediaType == MediaType.MOVIE ? DISCOVER_MOVIE_ENDPOINT : DISCOVER_TV_ENDPOINT;

		try {
			return tmdbRestClient.get()
				.uri(uriBuilder -> {
					uriBuilder.path(endpoint)
						.queryParam("page", filters.page())
						.queryParam("sort_by", filters.sortBy().tmdbValue(mediaType));

					if (StringUtils.hasText(filters.genres())) {
						uriBuilder.queryParam("with_genres", filters.genres());
					}
					if (filters.voteAverageMin() != null) {
						uriBuilder.queryParam("vote_average.gte", filters.voteAverageMin());
					}
					if (filters.voteAverageMax() != null) {
						uriBuilder.queryParam("vote_average.lte", filters.voteAverageMax());
					}
					if (filters.runtimeMin() != null) {
						uriBuilder.queryParam("with_runtime.gte", filters.runtimeMin());
					}
					if (filters.runtimeMax() != null) {
						uriBuilder.queryParam("with_runtime.lte", filters.runtimeMax());
					}
					if (dateRange != null) {
						String datePrefix = mediaType == MediaType.TV ? "first_air_date" : "primary_release_date";
						uriBuilder.queryParam(datePrefix + ".gte", dateRange.gte())
							.queryParam(datePrefix + ".lte", dateRange.lte());
					}
					if (mediaType == MediaType.MOVIE) {
						String certificationCeiling = certificationCeilingFor(filters.certifications());
						if (certificationCeiling != null) {
							uriBuilder.queryParam("certification_country", "BR")
								.queryParam("certification.lte", certificationCeiling);
						}
					}

					return uriBuilder.build();
				})
				.retrieve()
				.body(TmdbDiscoverResponse.class);
		}
		catch (RestClientResponseException | ResourceAccessException ex) {
			throw new TmdbUnavailableException(endpoint, ex);
		}
	}

	/**
	 * Resolve os chips de classificacao indicativa selecionados (CSV, ex.: "Livre,16,18")
	 * para um unico teto TMDB (certification.lte), usando a classificacao mais restritiva
	 * marcada (semantica "ate esta classificacao"). Chips desconhecidos sao ignorados;
	 * retorna null quando nao ha nenhum chip valido selecionado.
	 */
	private String certificationCeilingFor(String certifications) {
		if (!StringUtils.hasText(certifications)) {
			return null;
		}

		return Arrays.stream(certifications.split(","))
			.map(String::trim)
			.map(value -> "Livre".equalsIgnoreCase(value) ? "L" : value)
			.filter(CERTIFICATION_ORDER::contains)
			.max(Comparator.comparingInt(CERTIFICATION_ORDER::indexOf))
			.orElse(null);
	}

	private DateRange dateRangeFromDecades(String releaseDecades) {
		if (!StringUtils.hasText(releaseDecades)) {
			return null;
		}

		List<Integer> decades = Arrays.stream(releaseDecades.split(","))
			.map(String::trim)
			.filter(StringUtils::hasText)
			.map(Integer::parseInt)
			.toList();

		if (decades.isEmpty()) {
			return null;
		}

		int startYear = Collections.min(decades);
		int endYear = Collections.max(decades) + 9;

		return new DateRange(startYear + "-01-01", endYear + "-12-31");
	}

	private Comparator<ScoredItem> comparatorFor(DiscoverSortBy sortBy) {
		Comparator<ScoredItem> ascending = switch (sortBy) {
			case POPULARITY_DESC, POPULARITY_ASC ->
				Comparator.comparing(scored -> orZero(scored.item().popularity()));
			case VOTE_AVERAGE_DESC, VOTE_AVERAGE_ASC ->
				Comparator.comparing(scored -> orZero(scored.item().voteAverage()));
			case RELEASE_DATE_DESC, RELEASE_DATE_ASC ->
				Comparator.comparing(this::releaseDateOf, Comparator.nullsFirst(Comparator.naturalOrder()));
		};

		return sortBy.descending() ? ascending.reversed() : ascending;
	}

	private String releaseDateOf(ScoredItem scored) {
		TmdbDiscoverItem item = scored.item();
		return scored.mediaType() == MediaType.TV ? item.firstAirDate() : item.releaseDate();
	}

	private double orZero(Double value) {
		return value != null ? value : 0.0;
	}

	private MediaSearchResult toMediaSearchResult(TmdbDiscoverItem item, MediaType mediaType) {
		boolean isMovie = mediaType == MediaType.MOVIE;
		String title = isMovie ? item.title() : item.name();
		String releaseDate = isMovie ? item.releaseDate() : item.firstAirDate();

		return new MediaSearchResult(
			item.id(),
			mediaType,
			title,
			extractYear(releaseDate),
			item.posterPath() != null ? POSTER_BASE_URL + item.posterPath() : null,
			item.overview(),
			item.voteAverage());
	}

	private record ScoredItem(TmdbDiscoverItem item, MediaType mediaType) {
	}

	private record DateRange(String gte, String lte) {
	}

	private MediaSearchResult toMediaSearchResult(TmdbMultiSearchItem item) {
		boolean isMovie = "movie".equals(item.mediaType());
		MediaType mediaType = isMovie ? MediaType.MOVIE : MediaType.TV;
		String title = isMovie ? item.title() : item.name();
		String releaseDate = isMovie ? item.releaseDate() : item.firstAirDate();

		return new MediaSearchResult(
			item.id(),
			mediaType,
			title,
			extractYear(releaseDate),
			item.posterPath() != null ? POSTER_BASE_URL + item.posterPath() : null,
			item.overview(),
			item.voteAverage());
	}

	private Integer extractYear(String date) {
		if (date == null || date.length() < 4) {
			return null;
		}

		try {
			return Integer.parseInt(date.substring(0, 4));
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}
}
