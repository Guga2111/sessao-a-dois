package com.app.tracking;

import java.util.Map;

/**
 * Static TMDB genre id -&gt; pt-BR name map (movie + tv genre lists combined), used to
 * resolve {@link MediaTrack#getGenreIds()} without calling TMDB again.
 */
final class GenreNames {

	private static final Map<Integer, String> NAMES = Map.ofEntries(
		Map.entry(28, "Ação"),
		Map.entry(12, "Aventura"),
		Map.entry(16, "Animação"),
		Map.entry(35, "Comédia"),
		Map.entry(80, "Crime"),
		Map.entry(99, "Documentário"),
		Map.entry(18, "Drama"),
		Map.entry(10751, "Família"),
		Map.entry(14, "Fantasia"),
		Map.entry(36, "História"),
		Map.entry(27, "Terror"),
		Map.entry(10402, "Música"),
		Map.entry(9648, "Mistério"),
		Map.entry(10749, "Romance"),
		Map.entry(878, "Ficção Científica"),
		Map.entry(10770, "Cinema TV"),
		Map.entry(53, "Thriller"),
		Map.entry(10752, "Guerra"),
		Map.entry(37, "Faroeste"),
		Map.entry(10759, "Ação e Aventura"),
		Map.entry(10762, "Infantil"),
		Map.entry(10763, "Notícias"),
		Map.entry(10764, "Reality"),
		Map.entry(10765, "Ficção Científica e Fantasia"),
		Map.entry(10766, "Novela"),
		Map.entry(10767, "Talk Show"),
		Map.entry(10768, "Guerra e Política")
	);

	private GenreNames() {
	}

	static String nameFor(Integer genreId) {
		return genreId == null ? null : NAMES.get(genreId);
	}
}
