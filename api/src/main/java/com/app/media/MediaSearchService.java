package com.app.media;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class MediaSearchService {

	private static final String POSTER_BASE_URL = "https://image.tmdb.org/t/p/w500";

	private final RestClient tmdbRestClient;

	public MediaSearchService(RestClient tmdbRestClient) {
		this.tmdbRestClient = tmdbRestClient;
	}

	public List<MediaSearchResult> search(String query) {
		TmdbMultiSearchResponse response = tmdbRestClient.get()
			.uri(uriBuilder -> uriBuilder.path("/search/multi").queryParam("query", query).build())
			.retrieve()
			.body(TmdbMultiSearchResponse.class);

		if (response == null || response.results() == null) {
			return List.of();
		}

		return response.results().stream()
			.filter(item -> "movie".equals(item.mediaType()) || "tv".equals(item.mediaType()))
			.map(this::toMediaSearchResult)
			.toList();
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
