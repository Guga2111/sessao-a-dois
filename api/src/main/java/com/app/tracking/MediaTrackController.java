package com.app.tracking;

import com.app.common.PageResponse;
import com.app.couple.CoupleFacade;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/tracking")
public class MediaTrackController {

	private final MediaTrackService mediaTrackService;
	private final UserReviewService userReviewService;
	private final CoupleFacade coupleFacade;
	private final StatsService statsService;

	public MediaTrackController(MediaTrackService mediaTrackService, UserReviewService userReviewService,
			CoupleFacade coupleFacade, StatsService statsService) {
		this.mediaTrackService = mediaTrackService;
		this.userReviewService = userReviewService;
		this.coupleFacade = coupleFacade;
		this.statsService = statsService;
	}

	@GetMapping("/stats")
	public ResponseEntity<StatsResponse> stats(@AuthenticationPrincipal UUID userId) {
		Optional<UUID> coupleId = coupleFacade.findActiveCoupleId(userId);
		StatsResponse response = coupleId
			.map(statsService::getStats)
			.orElseGet(statsService::emptyStats);
		return ResponseEntity.ok(response);
	}

	/** Chaves (mediaType + tmdbId) do casal, sem reviews nem metadados — usado pela tela de Match. */
	@GetMapping("/keys")
	public ResponseEntity<List<TrackKeyResponse>> listKeys(@AuthenticationPrincipal UUID userId) {
		UUID coupleId = currentCoupleId(userId);
		return ResponseEntity.ok(mediaTrackService.listKeys(coupleId));
	}

	/** With {@code status}: paginated (default page=0, size=20, size capped server-side). */
	@GetMapping(params = "status")
	public ResponseEntity<PageResponse<MediaTrackResponse>> listByStatus(@AuthenticationPrincipal UUID userId,
			@RequestParam MediaStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		UUID coupleId = currentCoupleId(userId);
		return ResponseEntity.ok(PageResponse.from(mediaTrackService.listByStatusPaged(coupleId, status, page, size)));
	}

	@PostMapping
	public ResponseEntity<MediaTrackResponse> create(@AuthenticationPrincipal UUID userId,
			@Valid @RequestBody CreateMediaTrackRequest request) {
		UUID coupleId = currentCoupleId(userId);
		MediaTrackResponse response = mediaTrackService.addTrack(coupleId, userId, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@PatchMapping("/{id}/watch")
	public ResponseEntity<MediaTrackResponse> markAsWatched(@AuthenticationPrincipal UUID userId,
			@PathVariable UUID id, @Valid @RequestBody WatchRequest request) {
		UUID coupleId = currentCoupleId(userId);
		return ResponseEntity.ok(mediaTrackService.markAsWatched(id, coupleId, userId, request));
	}

	@PatchMapping("/{id}/status")
	public ResponseEntity<MediaTrackResponse> updateStatus(@AuthenticationPrincipal UUID userId,
			@PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest request) {
		UUID coupleId = currentCoupleId(userId);
		return ResponseEntity.ok(mediaTrackService.startWatching(id, coupleId, request.status()));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
		UUID coupleId = currentCoupleId(userId);
		mediaTrackService.deleteTrack(id, coupleId);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/{id}/review")
	public ResponseEntity<MediaTrackResponse> upsertReview(@AuthenticationPrincipal UUID userId,
			@PathVariable UUID id, @Valid @RequestBody UpsertReviewRequest request) {
		UUID coupleId = currentCoupleId(userId);
		MediaTrackResponse response = userReviewService.upsertReview(id, userId, coupleId, request);
		return ResponseEntity.ok(response);
	}

	private UUID currentCoupleId(UUID userId) {
		return coupleFacade.requireActiveCoupleId(userId);
	}
}
