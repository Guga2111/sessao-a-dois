package com.app.notification;

import com.app.couple.Couple;
import com.app.couple.CoupleRepository;
import com.app.media.MediaType;

import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class NotificationRepositoryTest {

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private CoupleRepository coupleRepository;

	@Autowired
	private EntityManager entityManager;

	private Couple persistedCouple() {
		return coupleRepository.save(new Couple(UUID.randomUUID(), "NTC" + UUID.randomUUID().toString().substring(0, 4)));
	}

	private Notification newNotification(Couple couple, UUID recipientUserId) {
		return new Notification(couple, recipientUserId, NotificationType.MATCH, 603L, MediaType.MOVIE,
				"The Matrix", UUID.randomUUID());
	}

	@Test
	void findByRecipientUserIdOrderByCreatedAtDescReturnsOnlyRecipientNotificationsNewestFirst() {
		Couple couple = persistedCouple();
		UUID recipientId = UUID.randomUUID();
		UUID otherId = UUID.randomUUID();

		Notification first = notificationRepository.save(newNotification(couple, recipientId));
		Notification second = notificationRepository.save(newNotification(couple, recipientId));
		notificationRepository.save(newNotification(couple, otherId));

		var page = notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(recipientId, Pageable.unpaged());

		assertThat(page.getContent()).hasSize(2);
		assertThat(page.getContent()).extracting(Notification::getId).containsExactlyInAnyOrder(first.getId(),
				second.getId());
	}

	@Test
	void countByRecipientUserIdAndReadFalseCountsOnlyUnread() {
		Couple couple = persistedCouple();
		UUID recipientId = UUID.randomUUID();

		Notification unread = notificationRepository.save(newNotification(couple, recipientId));
		Notification read = newNotification(couple, recipientId);
		read.setRead(true);
		notificationRepository.save(read);

		assertThat(notificationRepository.countByRecipientUserIdAndReadFalse(recipientId)).isEqualTo(1);
		assertThat(unread.isRead()).isFalse();
	}

	@Test
	void findByRecipientUserIdOrderByCreatedAtDescIsEmptyForUnknownRecipient() {
		var page = notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(UUID.randomUUID(),
				PageRequest.of(0, 10));

		assertThat(page.getContent()).isEmpty();
	}

	@Test
	void ratingRequestTypeIsPersistedAsItsEnumName() {
		Couple couple = persistedCouple();
		Notification saved = notificationRepository.save(new Notification(couple, UUID.randomUUID(),
				NotificationType.RATING_REQUEST, 603L, MediaType.MOVIE, "The Matrix", UUID.randomUUID()));
		entityManager.flush();
		entityManager.clear();

		Object storedType = entityManager.createNativeQuery("SELECT type FROM notification WHERE id = ?")
			.setParameter(1, saved.getId())
			.getSingleResult();

		assertThat(storedType).isEqualTo("RATING_REQUEST");
		assertThat(notificationRepository.findById(saved.getId()))
			.get()
			.extracting(Notification::getType)
			.isEqualTo(NotificationType.RATING_REQUEST);
	}

	@Test
	void mediaTrackIdIsPersistedForRatingRequestAndNullForMatch() {
		Couple couple = persistedCouple();
		UUID mediaTrackId = UUID.randomUUID();

		Notification ratingRequest = notificationRepository.save(new Notification(couple, UUID.randomUUID(),
				NotificationType.RATING_REQUEST, 603L, MediaType.MOVIE, "The Matrix", UUID.randomUUID(),
				mediaTrackId));
		Notification match = notificationRepository.save(newNotification(couple, UUID.randomUUID()));
		entityManager.flush();
		entityManager.clear();

		assertThat(notificationRepository.findById(ratingRequest.getId()))
			.get()
			.extracting(Notification::getMediaTrackId)
			.isEqualTo(mediaTrackId);
		assertThat(notificationRepository.findById(match.getId()))
			.get()
			.extracting(Notification::getMediaTrackId)
			.isNull();
	}

	@Test
	void deleteByCreatedAtBeforeRemovesOnlyNotificationsOlderThanCutoff() {
		Couple couple = persistedCouple();
		UUID recipientId = UUID.randomUUID();

		Notification old = notificationRepository.save(newNotification(couple, recipientId));
		Notification recent = notificationRepository.save(newNotification(couple, recipientId));

		entityManager.createNativeQuery("UPDATE notification SET created_at = ? WHERE id = ?")
			.setParameter(1, Instant.now().minus(31, ChronoUnit.DAYS))
			.setParameter(2, old.getId())
			.executeUpdate();
		entityManager.createNativeQuery("UPDATE notification SET created_at = ? WHERE id = ?")
			.setParameter(1, Instant.now().minus(10, ChronoUnit.DAYS))
			.setParameter(2, recent.getId())
			.executeUpdate();
		entityManager.flush();
		entityManager.clear();

		int removed = notificationRepository.deleteByCreatedAtBefore(Instant.now().minus(30, ChronoUnit.DAYS));

		assertThat(removed).isEqualTo(1);
		assertThat(notificationRepository.findById(old.getId())).isEmpty();
		assertThat(notificationRepository.findById(recent.getId())).isPresent();
	}

	@Test
	void existsUnreadRatingRequestIsScopedToRecipientCoupleTmdbIdAndType() {
		Couple couple = persistedCouple();
		Couple otherCouple = persistedCouple();
		UUID recipientId = UUID.randomUUID();

		notificationRepository.save(new Notification(couple, recipientId, NotificationType.RATING_REQUEST, 603L,
				MediaType.MOVIE, "The Matrix", UUID.randomUUID(), UUID.randomUUID()));

		assertThat(notificationRepository.existsByRecipientUserIdAndCoupleIdAndTmdbIdAndTypeAndReadFalse(
				recipientId, couple.getId(), 603L, NotificationType.RATING_REQUEST)).isTrue();
		// outro casal, outro titulo, outro destinatario e outro tipo nao contam
		assertThat(notificationRepository.existsByRecipientUserIdAndCoupleIdAndTmdbIdAndTypeAndReadFalse(
				recipientId, otherCouple.getId(), 603L, NotificationType.RATING_REQUEST)).isFalse();
		assertThat(notificationRepository.existsByRecipientUserIdAndCoupleIdAndTmdbIdAndTypeAndReadFalse(
				recipientId, couple.getId(), 604L, NotificationType.RATING_REQUEST)).isFalse();
		assertThat(notificationRepository.existsByRecipientUserIdAndCoupleIdAndTmdbIdAndTypeAndReadFalse(
				UUID.randomUUID(), couple.getId(), 603L, NotificationType.RATING_REQUEST)).isFalse();
		assertThat(notificationRepository.existsByRecipientUserIdAndCoupleIdAndTmdbIdAndTypeAndReadFalse(
				recipientId, couple.getId(), 603L, NotificationType.MATCH)).isFalse();
	}

	@Test
	void existsUnreadRatingRequestIgnoresAlreadyReadNotifications() {
		Couple couple = persistedCouple();
		UUID recipientId = UUID.randomUUID();

		Notification notification = notificationRepository.save(new Notification(couple, recipientId,
				NotificationType.RATING_REQUEST, 603L, MediaType.MOVIE, "The Matrix", UUID.randomUUID(),
				UUID.randomUUID()));
		notification.setRead(true);
		notificationRepository.save(notification);

		assertThat(notificationRepository.existsByRecipientUserIdAndCoupleIdAndTmdbIdAndTypeAndReadFalse(
				recipientId, couple.getId(), 603L, NotificationType.RATING_REQUEST)).isFalse();
	}

	@Test
	void findsOnlyUnreadRatingRequestsOfThatRecipientCoupleAndTitle() {
		Couple couple = persistedCouple();
		Couple otherCouple = persistedCouple();
		UUID recipientId = UUID.randomUUID();

		Notification pending = notificationRepository.save(new Notification(couple, recipientId,
				NotificationType.RATING_REQUEST, 603L, MediaType.MOVIE, "The Matrix", UUID.randomUUID(),
				UUID.randomUUID()));
		// ruidos que a query nao pode capturar
		Notification alreadyRead = notificationRepository.save(new Notification(couple, recipientId,
				NotificationType.RATING_REQUEST, 603L, MediaType.MOVIE, "The Matrix", UUID.randomUUID(),
				UUID.randomUUID()));
		alreadyRead.setRead(true);
		notificationRepository.save(alreadyRead);
		notificationRepository.save(new Notification(couple, recipientId, NotificationType.MATCH, 603L,
				MediaType.MOVIE, "The Matrix", UUID.randomUUID()));
		notificationRepository.save(new Notification(couple, recipientId, NotificationType.RATING_REQUEST, 604L,
				MediaType.MOVIE, "Outro", UUID.randomUUID(), UUID.randomUUID()));
		notificationRepository.save(new Notification(otherCouple, recipientId, NotificationType.RATING_REQUEST,
				603L, MediaType.MOVIE, "The Matrix", UUID.randomUUID(), UUID.randomUUID()));

		assertThat(notificationRepository.findByRecipientUserIdAndCoupleIdAndTmdbIdAndTypeAndReadFalse(
				recipientId, couple.getId(), 603L, NotificationType.RATING_REQUEST))
			.extracting(Notification::getId)
			.containsExactly(pending.getId());
	}

	@Test
	void findUnreadRatingRequestsIsEmptyWhenThereIsNoPendingRequest() {
		Couple couple = persistedCouple();

		assertThat(notificationRepository.findByRecipientUserIdAndCoupleIdAndTmdbIdAndTypeAndReadFalse(
				UUID.randomUUID(), couple.getId(), 603L, NotificationType.RATING_REQUEST)).isEmpty();
	}

	@Test
	void deletesOnlyTheRatingRequestsOfThatCoupleAndTitle() {
		Couple couple = persistedCouple();
		Couple otherCouple = persistedCouple();
		UUID recipientId = UUID.randomUUID();

		notificationRepository.save(new Notification(couple, recipientId, NotificationType.RATING_REQUEST, 603L,
				MediaType.MOVIE, "The Matrix", UUID.randomUUID(), UUID.randomUUID()));
		Notification read = notificationRepository.save(new Notification(couple, recipientId,
				NotificationType.RATING_REQUEST, 603L, MediaType.MOVIE, "The Matrix", UUID.randomUUID(),
				UUID.randomUUID()));
		read.setRead(true);
		notificationRepository.save(read);
		// ruidos que a delecao nao pode capturar
		Notification match = notificationRepository.save(new Notification(couple, recipientId,
				NotificationType.MATCH, 603L, MediaType.MOVIE, "The Matrix", UUID.randomUUID()));
		Notification otherTitle = notificationRepository.save(new Notification(couple, recipientId,
				NotificationType.RATING_REQUEST, 604L, MediaType.MOVIE, "Outro", UUID.randomUUID(),
				UUID.randomUUID()));
		Notification otherCoupleRequest = notificationRepository.save(new Notification(otherCouple, recipientId,
				NotificationType.RATING_REQUEST, 603L, MediaType.MOVIE, "The Matrix", UUID.randomUUID(),
				UUID.randomUUID()));

		long deleted = notificationRepository.deleteByCoupleIdAndTmdbIdAndType(couple.getId(), 603L,
				NotificationType.RATING_REQUEST);

		assertThat(deleted).isEqualTo(2);
		assertThat(notificationRepository.findAll()).extracting(Notification::getId)
			.containsExactlyInAnyOrder(match.getId(), otherTitle.getId(), otherCoupleRequest.getId());
	}

	@Test
	void deleteRatingRequestsIsANoOpWhenThereIsNoneForTheTitle() {
		Couple couple = persistedCouple();
		notificationRepository.save(newNotification(couple, UUID.randomUUID()));

		assertThat(notificationRepository.deleteByCoupleIdAndTmdbIdAndType(couple.getId(), 603L,
				NotificationType.RATING_REQUEST)).isZero();
		assertThat(notificationRepository.findAll()).hasSize(1);
	}
}
