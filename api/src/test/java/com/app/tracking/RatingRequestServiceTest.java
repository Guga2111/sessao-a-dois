package com.app.tracking;

import com.app.couple.CoupleFacade;
import com.app.media.MediaDetails;
import com.app.media.MediaDetailsService;
import com.app.media.MediaType;
import com.app.notification.Notification;
import com.app.notification.NotificationRepository;
import com.app.notification.NotificationService;
import com.app.notification.NotificationType;
import com.app.user.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingRequestServiceTest {

	private static final Long TMDB_ID = 603L;

	@Mock
	private NotificationService notificationService;

	@Mock
	private NotificationRepository notificationRepository;

	@Mock
	private MediaDetailsService mediaDetailsService;

	@Mock
	private CoupleFacade coupleFacade;

	private RatingRequestService ratingRequestService;

	private final UUID actorId = UUID.randomUUID();
	private final UUID partnerId = UUID.randomUUID();
	private final UUID coupleId = UUID.randomUUID();
	private final UUID trackId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		ratingRequestService = new RatingRequestService(notificationService, notificationRepository,
				mediaDetailsService, coupleFacade);
	}

	/** Os membros do casal agora chegam pela porta, nao mais pela entidade pendurada no track. */
	private void stubMembers(UUID... memberIds) {
		when(coupleFacade.memberIds(coupleId)).thenReturn(List.of(memberIds));
	}

	private MediaTrack track() {
		MediaTrack track = new MediaTrack(coupleId, TMDB_ID, MediaType.MOVIE, MediaStatus.WATCHED);
		ReflectionTestUtils.setField(track, "id", trackId);
		return track;
	}

	private void addReview(MediaTrack track, UUID userId, Integer rating) {
		User user = new User("Membro", userId + "@example.com", "hash");
		ReflectionTestUtils.setField(user, "id", userId);
		track.getReviews().add(new UserReview(track, user, rating, null));
	}

	private void stubTitle(String title) {
		when(mediaDetailsService.getDetails(MediaType.MOVIE, TMDB_ID))
			.thenReturn(new MediaDetails(TMDB_ID, MediaType.MOVIE, title, 1999, null, null, null, null, null,
					136, null));
	}

	@Test
	void notifiesPartnerWhenOnlyActorHasRated() {
		MediaTrack track = track();
		stubMembers(actorId, partnerId);
		addReview(track, actorId, 5);
		stubTitle("Matrix");
		when(notificationRepository.existsByRecipientUserIdAndCoupleIdAndTmdbIdAndTypeAndReadFalse(
				partnerId, coupleId, TMDB_ID, NotificationType.RATING_REQUEST)).thenReturn(false);

		ratingRequestService.onRatingRegistered(track, actorId);

		verify(notificationService).notifyRatingRequest(coupleId, partnerId, actorId, TMDB_ID,
				MediaType.MOVIE, "Matrix", trackId);
	}

	@Test
	void notifiesPartnerWhenActorIsUser2() {
		MediaTrack track = track();
		stubMembers(partnerId, actorId);
		addReview(track, actorId, 3);
		stubTitle("Matrix");
		when(notificationRepository.existsByRecipientUserIdAndCoupleIdAndTmdbIdAndTypeAndReadFalse(
				partnerId, coupleId, TMDB_ID, NotificationType.RATING_REQUEST)).thenReturn(false);

		ratingRequestService.onRatingRegistered(track, actorId);

		verify(notificationService).notifyRatingRequest(coupleId, partnerId, actorId, TMDB_ID,
				MediaType.MOVIE, "Matrix", trackId);
	}

	@Test
	void doesNotNotifyWhenPartnerAlreadyRated() {
		MediaTrack track = track();
		stubMembers(actorId, partnerId);
		addReview(track, actorId, 5);
		addReview(track, partnerId, 4);

		ratingRequestService.onRatingRegistered(track, actorId);

		verifyNoInteractions(notificationService);
		verify(notificationRepository, never()).existsByRecipientUserIdAndCoupleIdAndTmdbIdAndTypeAndReadFalse(
				any(), any(), any(), any());
	}

	@Test
	void doesNotNotifyWhenCoupleHasNoPartner() {
		MediaTrack track = track();
		stubMembers(actorId);
		addReview(track, actorId, 5);

		ratingRequestService.onRatingRegistered(track, actorId);

		verifyNoInteractions(notificationService);
	}

	@Test
	void doesNotNotifyWhenActorHasNoRating() {
		MediaTrack track = track();
		addReview(track, actorId, null);

		ratingRequestService.onRatingRegistered(track, actorId);

		verifyNoInteractions(notificationService);
		verifyNoInteractions(mediaDetailsService);
	}

	@Test
	void doesNotNotifyTwiceWhenAnUnreadRequestAlreadyExists() {
		MediaTrack track = track();
		stubMembers(actorId, partnerId);
		addReview(track, actorId, 5);
		when(notificationRepository.existsByRecipientUserIdAndCoupleIdAndTmdbIdAndTypeAndReadFalse(
				partnerId, coupleId, TMDB_ID, NotificationType.RATING_REQUEST)).thenReturn(true);

		ratingRequestService.onRatingRegistered(track, actorId);

		verifyNoInteractions(notificationService);
		verifyNoInteractions(mediaDetailsService);
	}

	@Test
	void fallsBackToGenericTitleWhenTmdbFails() {
		MediaTrack track = track();
		stubMembers(actorId, partnerId);
		addReview(track, actorId, 5);
		when(mediaDetailsService.getDetails(MediaType.MOVIE, TMDB_ID))
			.thenThrow(new RuntimeException("TMDB indisponivel"));
		when(notificationRepository.existsByRecipientUserIdAndCoupleIdAndTmdbIdAndTypeAndReadFalse(
				partnerId, coupleId, TMDB_ID, NotificationType.RATING_REQUEST)).thenReturn(false);

		ratingRequestService.onRatingRegistered(track, actorId);

		verify(notificationService).notifyRatingRequest(eq(coupleId), eq(partnerId), eq(actorId),
				eq(TMDB_ID), eq(MediaType.MOVIE), eq(RatingRequestService.FALLBACK_TITLE), eq(trackId));
	}

	@Test
	void marksTheActorsOwnPendingRequestAsReadWhenHeRates() {
		MediaTrack track = track();
		stubMembers(partnerId, actorId);
		addReview(track, actorId, 4);
		addReview(track, partnerId, 5);
		Notification pending = new Notification(track.getCoupleId(), actorId, NotificationType.RATING_REQUEST,
				TMDB_ID, MediaType.MOVIE, "Matrix", partnerId, trackId);
		when(notificationRepository.findByRecipientUserIdAndCoupleIdAndTmdbIdAndTypeAndReadFalse(actorId,
				coupleId, TMDB_ID, NotificationType.RATING_REQUEST))
			.thenReturn(List.of(pending));

		ratingRequestService.onRatingRegistered(track, actorId);

		assertThat(pending.isRead()).isTrue();
		verify(notificationRepository).saveAll(List.of(pending));
	}

	@Test
	void doesNothingWhenTheActorHasNoPendingRequest() {
		MediaTrack track = track();
		stubMembers(actorId, partnerId);
		addReview(track, actorId, 5);
		addReview(track, partnerId, 4);

		ratingRequestService.onRatingRegistered(track, actorId);

		verify(notificationRepository).findByRecipientUserIdAndCoupleIdAndTmdbIdAndTypeAndReadFalse(actorId,
				coupleId, TMDB_ID, NotificationType.RATING_REQUEST);
		verify(notificationRepository, never()).saveAll(any());
		verifyNoInteractions(notificationService);
	}

	@Test
	void doesNotResolveAnythingWhenTheActorDidNotRate() {
		MediaTrack track = track();
		addReview(track, actorId, null);

		ratingRequestService.onRatingRegistered(track, actorId);

		verifyNoInteractions(notificationRepository);
	}

	/** Sem couple pendurado no track, achar o parceiro passa a custar uma consulta - que tem de ser UMA. */
	@Test
	void resolvesCoupleMembersOnlyOncePerRatingRegistered() {
		MediaTrack track = track();
		stubMembers(actorId, partnerId);
		addReview(track, actorId, 5);
		stubTitle("Matrix");
		when(notificationRepository.existsByRecipientUserIdAndCoupleIdAndTmdbIdAndTypeAndReadFalse(
				partnerId, coupleId, TMDB_ID, NotificationType.RATING_REQUEST)).thenReturn(false);

		ratingRequestService.onRatingRegistered(track, actorId);

		verify(coupleFacade, times(1)).memberIds(coupleId);
	}

	@Test
	void doesNotNotifyAnyoneWhenTheActorIsNotAMemberOfTheCouple() {
		MediaTrack track = track();
		stubMembers(UUID.randomUUID(), UUID.randomUUID());
		addReview(track, actorId, 5);

		ratingRequestService.onRatingRegistered(track, actorId);

		verifyNoInteractions(notificationService);
	}

	@Test
	void onTrackDeletedRemovesOnlyTheRatingRequestsOfThatCoupleAndTitle() {
		MediaTrack track = track();

		ratingRequestService.onTrackDeleted(track);

		verify(notificationRepository).deleteByCoupleIdAndTmdbIdAndType(coupleId, TMDB_ID,
				NotificationType.RATING_REQUEST);
		verifyNoInteractions(notificationService);
	}
}
