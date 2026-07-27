package com.app.media;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
record TmdbMultiSearchItem(
		long id,
		@JsonProperty("media_type") String mediaType,
		String title,
		String name,
		@JsonProperty("release_date") String releaseDate,
		@JsonProperty("first_air_date") String firstAirDate,
		@JsonProperty("poster_path") String posterPath,
		String overview,
		@JsonProperty("vote_average") Double voteAverage) {
}
