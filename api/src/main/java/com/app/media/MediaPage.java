package com.app.media;

import java.util.List;

public record MediaPage(List<MediaSearchResult> results, int page, int totalResults, int totalPages) {
}
