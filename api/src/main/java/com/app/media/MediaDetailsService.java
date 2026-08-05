package com.app.media;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class MediaDetailsService {

	private static final Logger log = LoggerFactory.getLogger(MediaDetailsService.class);

	private static final String POSTER_BASE_URL = "https://image.tmdb.org/t/p/w500";

	private static final String LOGO_BASE_URL = "https://image.tmdb.org/t/p/w92";

	private static final String WATCH_PROVIDERS_REGION = "BR";

	private final RestClient tmdbRestClient;

	public MediaDetailsService(RestClient tmdbRestClient) {
		this.tmdbRestClient = tmdbRestClient;
	}

	public MediaDetails getDetails(MediaType mediaType, long tmdbId) {
		String endpoint = "/" + mediaType.tmdbPath() + "/" + tmdbId;
		TmdbMediaDetailsResponse response;
		try {
			response = tmdbRestClient.get()
				.uri(uriBuilder -> uriBuilder
					.path("/{mediaType}/{tmdbId}")
					.queryParam("append_to_response", "watch/providers")
					.build(mediaType.tmdbPath(), tmdbId))
				.retrieve()
				.body(TmdbMediaDetailsResponse.class);
		}
		catch (HttpClientErrorException.NotFound ex) {
			throw new MediaNotFoundException(mediaType, tmdbId);
		}
		catch (RestClientResponseException | ResourceAccessException ex) {
			throw new TmdbUnavailableException(endpoint, ex);
		}

		if (response == null) {
			throw new MediaNotFoundException(mediaType, tmdbId);
		}

		return toMediaDetails(mediaType, response);
	}

	private MediaDetails toMediaDetails(MediaType mediaType, TmdbMediaDetailsResponse response) {
		boolean isMovie = mediaType == MediaType.MOVIE;
		String title = isMovie ? response.title() : response.name();
		String releaseDate = isMovie ? response.releaseDate() : response.firstAirDate();

		List<String> genres = response.genres() == null
			? List.of()
			: response.genres().stream().map(TmdbGenre::name).toList();
		List<Integer> genreIds = response.genres() == null
			? List.of()
			: response.genres().stream().map(genre -> (int) genre.id()).toList();

		return new MediaDetails(
			response.id(),
			mediaType,
			title,
			extractYear(releaseDate),
			response.posterPath() != null ? POSTER_BASE_URL + response.posterPath() : null,
			response.overview(),
			genres,
			genreIds,
			response.voteAverage(),
			isMovie ? response.runtime() : null,
			extractWatchProviders(response.watchProviders()));
	}

	private List<WatchProvider> extractWatchProviders(TmdbWatchProvidersResponse watchProviders) {
		if (watchProviders == null || watchProviders.results() == null) {
			return List.of();
		}

		TmdbWatchProviderCountry brazil = watchProviders.results().get(WATCH_PROVIDERS_REGION);
		if (brazil == null || brazil.flatrate() == null) {
			return List.of();
		}

		return brazil.flatrate().stream()
			.map(entry -> new WatchProvider(
				entry.providerName(),
				entry.logoPath() != null ? LOGO_BASE_URL + entry.logoPath() : null))
			.toList();
	}

	private Integer extractYear(String date) {
		if (date == null || date.length() < 4) {
			return null;
		}

		try {
			return Integer.parseInt(date.substring(0, 4));
		}
		catch (NumberFormatException ex) {
			log.warn("Data de lancamento em formato inesperado retornada pelo TMDB: {}", date);
			return null;
		}
	}
}
