package com.app.media;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
record TmdbMediaDetailsResponse(
		long id,
		String title,
		String name,
		@JsonProperty("release_date") String releaseDate,
		@JsonProperty("first_air_date") String firstAirDate,
		@JsonProperty("poster_path") String posterPath,
		String overview,
		List<TmdbGenre> genres,
		@JsonProperty("vote_average") Double voteAverage,
		Integer runtime,
		@JsonProperty("watch/providers") TmdbWatchProvidersResponse watchProviders) {
}
