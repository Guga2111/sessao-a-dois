package com.app.media;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record TmdbWatchProvidersResponse(Map<String, TmdbWatchProviderCountry> results) {
}
