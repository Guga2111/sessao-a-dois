package com.app.notification;

import com.app.couple.Couple;
import com.app.couple.CoupleRepository;
import com.app.media.MediaType;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class NotificationRepositoryTest {

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private CoupleRepository coupleRepository;

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
}
