package com.app.media;

/**
 * Filtros aceitos por GET /api/media/discover. {@code mediaType} nulo significa
 * "ambos" (filme + serie). {@code certifications} e um CSV de chips de classificacao
 * indicativa BR (ex.: "Livre,16,18"); so e aplicado a chamadas de filme
 * (certification_country=BR + certification.lte com a maior classificacao marcada),
 * TV ignora este filtro pois o TMDB nao suporta certification em /discover/tv.
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
