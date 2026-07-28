package com.app.media;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
record TmdbMultiSearchResponse(
		int page,
		List<TmdbMultiSearchItem> results,
		@JsonProperty("total_pages") int totalPages,
		@JsonProperty("total_results") int totalResults) {
}
