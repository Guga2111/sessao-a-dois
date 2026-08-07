package com.app.tracking;

import com.app.couple.CoupleFacade;
import com.app.media.MediaDetails;
import com.app.media.MediaDetailsService;
import com.app.media.MediaType;
import com.app.user.User;
import com.app.user.UserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Proves the entity created by the match path (via {@link TrackingFacade}) and the entity created
 * by the manual path ({@link MediaTrackService#addTrack}) end up with the same relevant fields
 * populated the same way, given the same TMDB response.
 */
@ExtendWith(MockitoExtension.class)
class TrackCreationParityTest {

	@Mock
	private MediaTrackRepository mediaTrackRepository;

	@Mock
	private UserReviewRepository userReviewRepository;

	@Mock
	private CoupleFacade coupleFacade;

	@Mock
	private UserRepository userRepository;

	@Mock
	private MediaDetailsService mediaDetailsService;

	@Mock
	private RatingRequestService ratingRequestService;

	@Test
	void matchPathAndManualPathCreateEquivalentTracks() {
		UUID coupleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		User user = new User("Ana", "ana@example.com", "hash");
		ReflectionTestUtils.setField(user, "id", userId);

		MediaDetails details = new MediaDetails(603L, MediaType.MOVIE, "Matrix", 1999, "/poster.jpg", null, null,
				List.of(28, 12), null, null, null);

		TrackingFacade trackingFacade = new TrackingFacade(mediaTrackRepository, userReviewRepository);
		ArgumentCaptor<MediaTrack> matchTrackCaptor = ArgumentCaptor.forClass(MediaTrack.class);
		when(mediaTrackRepository.save(matchTrackCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

		trackingFacade.createTrackFromMatch(coupleId, 603L, MediaType.MOVIE, details);
		MediaTrack matchTrack = matchTrackCaptor.getValue();

		MediaTrackService mediaTrackService = new MediaTrackService(mediaTrackRepository, coupleFacade,
				userRepository, mediaDetailsService, ratingRequestService, new MediaTrackMapper());
		CreateMediaTrackRequest request = new CreateMediaTrackRequest(603L, MediaType.MOVIE, MediaStatus.WANT_TO_SEE,
				null, null, null, null);

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(mediaDetailsService.getDetails(MediaType.MOVIE, 603L)).thenReturn(details);
		ArgumentCaptor<MediaTrack> manualTrackCaptor = ArgumentCaptor.forClass(MediaTrack.class);
		when(mediaTrackRepository.save(manualTrackCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

		mediaTrackService.addTrack(coupleId, userId, request);
		MediaTrack manualTrack = manualTrackCaptor.getValue();

		assertThat(matchTrack.getCoupleId()).isEqualTo(manualTrack.getCoupleId());
		assertThat(matchTrack.getTmdbId()).isEqualTo(manualTrack.getTmdbId());
		assertThat(matchTrack.getMediaType()).isEqualTo(manualTrack.getMediaType());
		assertThat(matchTrack.getStatus()).isEqualTo(manualTrack.getStatus());
		assertThat(matchTrack.getStatus()).isEqualTo(MediaStatus.WANT_TO_SEE);
		assertThat(matchTrack.getGenreIds()).isEqualTo(manualTrack.getGenreIds());
		assertThat(matchTrack.getTitle()).isEqualTo(manualTrack.getTitle());
		assertThat(matchTrack.getPosterUrl()).isEqualTo(manualTrack.getPosterUrl());
		assertThat(matchTrack.getReleaseYear()).isEqualTo(manualTrack.getReleaseYear());
	}
}
