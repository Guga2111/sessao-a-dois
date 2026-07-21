package com.app.tracking;

import com.app.couple.Couple;
import com.app.couple.CoupleService;

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
import java.util.UUID;

@RestController
@RequestMapping("/api/tracking")
public class MediaTrackController {

	private final MediaTrackService mediaTrackService;
	private final UserReviewService userReviewService;
	private final CoupleService coupleService;

	public MediaTrackController(MediaTrackService mediaTrackService, UserReviewService userReviewService,
			CoupleService coupleService) {
		this.mediaTrackService = mediaTrackService;
		this.userReviewService = userReviewService;
		this.coupleService = coupleService;
	}

	@GetMapping
	public ResponseEntity<List<MediaTrackResponse>> list(@AuthenticationPrincipal UUID userId,
			@RequestParam(required = false) MediaStatus status) {
		UUID coupleId = currentCoupleId(userId);
		return ResponseEntity.ok(mediaTrackService.listByStatus(coupleId, status));
	}

	@PostMapping
	public ResponseEntity<MediaTrackResponse> create(@AuthenticationPrincipal UUID userId,
			@Valid @RequestBody CreateMediaTrackRequest request) {
		UUID coupleId = currentCoupleId(userId);
		MediaTrackResponse response = mediaTrackService.addTrack(coupleId, userId, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@PatchMapping("/{id}/status")
	public ResponseEntity<MediaTrackResponse> updateStatus(@AuthenticationPrincipal UUID userId,
			@PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest request) {
		UUID coupleId = currentCoupleId(userId);
		MediaTrackResponse response = mediaTrackService.updateStatus(id, coupleId, request.status(),
			request.watchedDate());
		return ResponseEntity.ok(response);
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
		Couple couple = coupleService.getCurrentCouple(userId)
			.orElseThrow(() -> new ResourceNotFoundException("usuario nao pertence a nenhum casal"));
		return couple.getId();
	}
}
