package com.app.media;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/media")
public class MediaController {

	private final MediaSearchService mediaSearchService;

	private final MediaDetailsService mediaDetailsService;

	public MediaController(MediaSearchService mediaSearchService, MediaDetailsService mediaDetailsService) {
		this.mediaSearchService = mediaSearchService;
		this.mediaDetailsService = mediaDetailsService;
	}

	@GetMapping("/search")
	public ResponseEntity<MediaPage> search(
			@RequestParam(required = false) String q, @RequestParam(defaultValue = "1") int page) {
		if (!StringUtils.hasText(q)) {
			throw new InvalidSearchQueryException("O parametro 'q' e obrigatorio e nao pode ser vazio.");
		}

		return ResponseEntity.ok(mediaSearchService.search(q, page));
	}

	@GetMapping("/trending")
	public ResponseEntity<MediaPage> trending(@RequestParam(defaultValue = "1") int page) {
		return ResponseEntity.ok(mediaSearchService.trending(page));
	}

	@GetMapping("/discover")
	public ResponseEntity<MediaPage> discover(
			@RequestParam(required = false) String mediaType,
			@RequestParam(required = false) String genres,
			@RequestParam(required = false) String releaseDecades,
			@RequestParam(required = false) String certifications,
			@RequestParam(required = false) Double voteAverageMin,
			@RequestParam(required = false) Double voteAverageMax,
			@RequestParam(required = false) Integer runtimeMin,
			@RequestParam(required = false) Integer runtimeMax,
			@RequestParam(required = false) String sortBy,
			@RequestParam(defaultValue = "1") int page) {
		DiscoverFilters filters = new DiscoverFilters(
			MediaType.fromQueryValueOrNull(mediaType),
			genres,
			releaseDecades,
			certifications,
			voteAverageMin,
			voteAverageMax,
			runtimeMin,
			runtimeMax,
			DiscoverSortBy.fromValueOrDefault(sortBy),
			page);

		return ResponseEntity.ok(mediaSearchService.discover(filters));
	}

	@GetMapping("/genres")
	public ResponseEntity<List<MediaGenre>> genres() {
		return ResponseEntity.ok(mediaSearchService.genres());
	}

	@GetMapping("/{mediaType}/{tmdbId}")
	public ResponseEntity<MediaDetails> details(@PathVariable String mediaType, @PathVariable long tmdbId) {
		MediaType type = MediaType.fromPathValue(mediaType);
		return ResponseEntity.ok(mediaDetailsService.getDetails(type, tmdbId));
	}
}
