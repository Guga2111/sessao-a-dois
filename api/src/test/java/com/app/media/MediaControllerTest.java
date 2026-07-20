package com.app.media;

import java.util.List;
import java.util.UUID;

import com.app.security.JwtService;
import com.app.security.SecurityConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
	private JwtService jwtService;

	private static UsernamePasswordAuthenticationToken authenticatedUser() {
		return new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, List.of());
	}

	@Test
	void search_returnsMixedResultsForAuthenticatedUser() throws Exception {
		MediaSearchResult movie = new MediaSearchResult(
			603, MediaType.MOVIE, "Matrix", 1999, "https://image.tmdb.org/t/p/w500/poster.jpg", "overview", 8.2);
		when(mediaSearchService.search("matrix")).thenReturn(List.of(movie));

		mockMvc.perform(get("/api/media/search").param("q", "matrix").with(authentication(authenticatedUser())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].tmdbId").value(603))
			.andExpect(jsonPath("$[0].mediaType").value("MOVIE"))
			.andExpect(jsonPath("$[0].title").value("Matrix"));
	}

	@Test
	void search_returnsEmptyListWhenNoResultsFound() throws Exception {
		when(mediaSearchService.search("titulo-inexistente")).thenReturn(List.of());

		mockMvc.perform(get("/api/media/search")
				.param("q", "titulo-inexistente")
				.with(authentication(authenticatedUser())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$").isEmpty());
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
}
