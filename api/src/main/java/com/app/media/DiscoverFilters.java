package com.app.media;

/**
 * Filtros aceitos por GET /api/media/discover. {@code mediaType} nulo significa
 * "ambos" (filme + serie). {@code certifications} e aceito no contrato desta US-004
 * mas ainda nao e mapeado para o TMDB (classificacao indicativa e US-005).
 */
record DiscoverFilters(
		MediaType mediaType,
		String genres,
		String releaseDecades,
		String certifications,
		Double voteAverageMin,
		Double voteAverageMax,
		Integer runtimeMin,
		Integer runtimeMax,
		DiscoverSortBy sortBy,
		int page) {
}
