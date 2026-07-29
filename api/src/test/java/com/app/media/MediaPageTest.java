package com.app.media;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MediaPageTest {

	@Test
	void keepsTotalsUnchangedWhenWithinTmdbLimits() {
		MediaPage page = new MediaPage(List.of(), 1, 42, 3);

		assertThat(page.totalResults()).isEqualTo(42);
		assertThat(page.totalPages()).isEqualTo(3);
	}

	@Test
	void clampsTotalPagesToTmdbCapWhenTmdbReportsMore() {
		MediaPage page = new MediaPage(List.of(), 1, 999_999, 100_000);

		assertThat(page.totalPages()).isEqualTo(500);
	}

	@Test
	void clampsTotalResultsToTmdbCapWhenTmdbReportsMore() {
		MediaPage page = new MediaPage(List.of(), 1, 999_999, 100_000);

		assertThat(page.totalResults()).isEqualTo(10_000);
	}
}
