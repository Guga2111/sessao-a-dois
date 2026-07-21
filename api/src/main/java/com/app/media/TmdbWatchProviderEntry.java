package com.app.media;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
record TmdbWatchProviderEntry(
		@JsonProperty("provider_name") String providerName,
		@JsonProperty("logo_path") String logoPath) {
}
