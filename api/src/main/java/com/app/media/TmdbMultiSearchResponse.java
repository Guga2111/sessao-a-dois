package com.app.media;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record TmdbMultiSearchResponse(List<TmdbMultiSearchItem> results) {
}
