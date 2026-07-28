package com.app.media;

import java.util.List;
import java.util.UUID;

import com.app.security.JwtService;
import com.app.security.SecurityConfig;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MediaController.class)
@Import(SecurityConfig.class)
class MediaControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MediaSearchService mediaSearchService;

	@MockitoBean
	private MediaDetailsService mediaDetailsService;

	@MockitoBean
	private JwtService jwtService;

	private static UsernamePasswordAuthenticationToken authenticatedUser() {
		return new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, List.of());
	}

	@Test
	void search_returnsMixedResultsForAuthenticatedUser() throws Exception {
		MediaSearchResult movie = new MediaSearchResult(
			603, MediaType.MOVIE, "Matrix", 1999, "https://image.tmdb.org/t/p/w500/poster.jpg", "overview", 8.2);
		MediaPage page = new MediaPage(List.of(movie), 1, 1, 1);
		when(mediaSearchService.search("matrix", 1)).thenReturn(page);

		mockMvc.perform(get("/api/media/search").param("q", "matrix").with(authentication(authenticatedUser())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.results[0].tmdbId").value(603))
			.andExpect(jsonPath("$.results[0].mediaType").value("MOVIE"))
			.andExpect(jsonPath("$.results[0].title").value("Matrix"))
			.andExpect(jsonPath("$.page").value(1));
	}

	@Test
	void search_passesRequestedPageToService() throws Exception {
		when(mediaSearchService.search("matrix", 2)).thenReturn(new MediaPage(List.of(), 2, 0, 0));

		mockMvc.perform(get("/api/media/search")
				.param("q", "matrix")
				.param("page", "2")
				.with(authentication(authenticatedUser())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page").value(2));
	}

	@Test
	void search_returnsEmptyPageWhenNoResultsFound() throws Exception {
		when(mediaSearchService.search("titulo-inexistente", 1)).thenReturn(new MediaPage(List.of(), 1, 0, 0));

		mockMvc.perform(get("/api/media/search")
				.param("q", "titulo-inexistente")
				.with(authentication(authenticatedUser())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.results").isArray())
			.andExpect(jsonPath("$.results").isEmpty());
	}

	@Test
	void search_rejectsMissingQueryParamWithBadRequest() throws Exception {
		mockMvc.perform(get("/api/media/search").with(authentication(authenticatedUser())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").exists());
	}

	@Test
	void search_rejectsBlankQueryParamWithBadRequest() throws Exception {
		mockMvc.perform(get("/api/media/search").param("q", "   ").with(authentication(authenticatedUser())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").exists());
	}

	@Test
	void search_deniesAccessWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/media/search").param("q", "matrix"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void details_returnsMediaDetailsForAuthenticatedUser() throws Exception {
		MediaDetails details = new MediaDetails(
			603, MediaType.MOVIE, "Matrix", 1999, "https://image.tmdb.org/t/p/w500/poster.jpg",
			"overview", List.of("Acao"), List.of(28), 8.2, 136,
			List.of(new WatchProvider("Netflix", "https://image.tmdb.org/t/p/w92/netflix.jpg")));
		when(mediaDetailsService.getDetails(MediaType.MOVIE, 603)).thenReturn(details);

		mockMvc.perform(get("/api/media/movie/603").with(authentication(authenticatedUser())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tmdbId").value(603))
			.andExpect(jsonPath("$.mediaType").value("MOVIE"))
			.andExpect(jsonPath("$.title").value("Matrix"))
			.andExpect(jsonPath("$.watchProviders[0].name").value("Netflix"));
	}

	@Test
	void details_rejectsInvalidMediaTypeWithBadRequest() throws Exception {
		mockMvc.perform(get("/api/media/song/603").with(authentication(authenticatedUser())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").exists());
	}

	@Test
	void details_returnsNotFoundWhenTmdbIdDoesNotExist() throws Exception {
		when(mediaDetailsService.getDetails(MediaType.MOVIE, 999999999))
			.thenThrow(new MediaNotFoundException(MediaType.MOVIE, 999999999));

		mockMvc.perform(get("/api/media/movie/999999999").with(authentication(authenticatedUser())))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").exists());
	}

	@Test
	void details_deniesAccessWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/media/movie/603"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void search_returnsBadGatewayWhenTmdbIsUnavailable() throws Exception {
		when(mediaSearchService.search("matrix", 1))
			.thenThrow(new TmdbUnavailableException("/search/multi", new RuntimeException("boom")));

		mockMvc.perform(get("/api/media/search").param("q", "matrix").with(authentication(authenticatedUser())))
			.andExpect(status().isBadGateway())
			.andExpect(jsonPath("$.message").value("Nao foi possivel buscar dados no momento. Tente novamente mais tarde."));
	}

	@Test
	void details_returnsBadGatewayWhenTmdbIsUnavailable() throws Exception {
		when(mediaDetailsService.getDetails(MediaType.MOVIE, 603))
			.thenThrow(new TmdbUnavailableException("/movie/603", new RuntimeException("boom")));

		mockMvc.perform(get("/api/media/movie/603").with(authentication(authenticatedUser())))
			.andExpect(status().isBadGateway())
			.andExpect(jsonPath("$.message").value("Nao foi possivel buscar dados no momento. Tente novamente mais tarde."));
	}

	@Test
	void trending_returnsPagedResultsForAuthenticatedUser() throws Exception {
		MediaSearchResult movie = new MediaSearchResult(
			603, MediaType.MOVIE, "Matrix", 1999, "https://image.tmdb.org/t/p/w500/poster.jpg", "overview", 8.2);
		MediaPage page = new MediaPage(List.of(movie), 1, 100, 5);
		when(mediaSearchService.trending(1)).thenReturn(page);

		mockMvc.perform(get("/api/media/trending").with(authentication(authenticatedUser())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.results[0].tmdbId").value(603))
			.andExpect(jsonPath("$.page").value(1))
			.andExpect(jsonPath("$.totalResults").value(100))
			.andExpect(jsonPath("$.totalPages").value(5));
	}

	@Test
	void trending_passesRequestedPageToService() throws Exception {
		when(mediaSearchService.trending(3)).thenReturn(new MediaPage(List.of(), 3, 0, 0));

		mockMvc.perform(get("/api/media/trending").param("page", "3").with(authentication(authenticatedUser())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page").value(3));
	}

	@Test
	void trending_deniesAccessWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/media/trending"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void trending_returnsBadGatewayWhenTmdbIsUnavailable() throws Exception {
		when(mediaSearchService.trending(1))
			.thenThrow(new TmdbUnavailableException("/trending/all/week", new RuntimeException("boom")));

		mockMvc.perform(get("/api/media/trending").with(authentication(authenticatedUser())))
			.andExpect(status().isBadGateway())
			.andExpect(jsonPath("$.message").value("Nao foi possivel buscar dados no momento. Tente novamente mais tarde."));
	}

	@Test
	void discover_returnsPagedResultsForAuthenticatedUser() throws Exception {
		MediaSearchResult movie = new MediaSearchResult(
			603, MediaType.MOVIE, "Matrix", 1999, "https://image.tmdb.org/t/p/w500/poster.jpg", "overview", 8.2);
		MediaPage page = new MediaPage(List.of(movie), 1, 40, 2);
		when(mediaSearchService.discover(any())).thenReturn(page);

		mockMvc.perform(get("/api/media/discover").with(authentication(authenticatedUser())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.results[0].tmdbId").value(603))
			.andExpect(jsonPath("$.totalResults").value(40))
			.andExpect(jsonPath("$.totalPages").value(2));
	}

	@Test
	void discover_passesParsedFiltersToService() throws Exception {
		ArgumentCaptor<DiscoverFilters> captor = ArgumentCaptor.forClass(DiscoverFilters.class);
		when(mediaSearchService.discover(captor.capture())).thenReturn(new MediaPage(List.of(), 2, 0, 0));

		mockMvc.perform(get("/api/media/discover")
				.param("mediaType", "movie")
				.param("genres", "28,12")
				.param("releaseDecades", "2020,2000")
				.param("voteAverageMin", "7.0")
				.param("voteAverageMax", "9.5")
				.param("runtimeMin", "60")
				.param("runtimeMax", "180")
				.param("sortBy", "vote_average.asc")
				.param("page", "2")
				.with(authentication(authenticatedUser())))
			.andExpect(status().isOk());

		DiscoverFilters filters = captor.getValue();
		assertThat(filters.mediaType()).isEqualTo(MediaType.MOVIE);
		assertThat(filters.genres()).isEqualTo("28,12");
		assertThat(filters.releaseDecades()).isEqualTo("2020,2000");
		assertThat(filters.voteAverageMin()).isEqualTo(7.0);
		assertThat(filters.voteAverageMax()).isEqualTo(9.5);
		assertThat(filters.runtimeMin()).isEqualTo(60);
		assertThat(filters.runtimeMax()).isEqualTo(180);
		assertThat(filters.sortBy()).isEqualTo(DiscoverSortBy.VOTE_AVERAGE_ASC);
		assertThat(filters.page()).isEqualTo(2);
	}

	@Test
	void discover_defaultsToNullMediaTypeAndPopularityDescWhenOmitted() throws Exception {
		ArgumentCaptor<DiscoverFilters> captor = ArgumentCaptor.forClass(DiscoverFilters.class);
		when(mediaSearchService.discover(captor.capture())).thenReturn(new MediaPage(List.of(), 1, 0, 0));

		mockMvc.perform(get("/api/media/discover").with(authentication(authenticatedUser())))
			.andExpect(status().isOk());

		DiscoverFilters filters = captor.getValue();
		assertThat(filters.mediaType()).isNull();
		assertThat(filters.sortBy()).isEqualTo(DiscoverSortBy.POPULARITY_DESC);
		assertThat(filters.page()).isEqualTo(1);
	}

	@Test
	void discover_rejectsInvalidMediaTypeWithBadRequest() throws Exception {
		mockMvc.perform(get("/api/media/discover").param("mediaType", "song").with(authentication(authenticatedUser())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").exists());
	}

	@Test
	void discover_rejectsInvalidSortByWithBadRequest() throws Exception {
		mockMvc.perform(get("/api/media/discover").param("sortBy", "random").with(authentication(authenticatedUser())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").exists());
	}

	@Test
	void discover_deniesAccessWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/media/discover"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void discover_returnsBadGatewayWhenTmdbIsUnavailable() throws Exception {
		when(mediaSearchService.discover(any()))
			.thenThrow(new TmdbUnavailableException("/discover/movie", new RuntimeException("boom")));

		mockMvc.perform(get("/api/media/discover").with(authentication(authenticatedUser())))
			.andExpect(status().isBadGateway())
			.andExpect(jsonPath("$.message").value("Nao foi possivel buscar dados no momento. Tente novamente mais tarde."));
	}
}
