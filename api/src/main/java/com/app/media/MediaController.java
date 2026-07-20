package com.app.media;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/media")
public class MediaController {

	private final MediaSearchService mediaSearchService;

	public MediaController(MediaSearchService mediaSearchService) {
		this.mediaSearchService = mediaSearchService;
	}

	@GetMapping("/search")
	public ResponseEntity<List<MediaSearchResult>> search(@RequestParam(required = false) String q) {
		if (!StringUtils.hasText(q)) {
			throw new InvalidSearchQueryException("O parametro 'q' e obrigatorio e nao pode ser vazio.");
		}

		return ResponseEntity.ok(mediaSearchService.search(q));
	}
}
