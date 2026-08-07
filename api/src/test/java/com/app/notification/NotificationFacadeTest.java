package com.app.notification;

import com.app.couple.Couple;
import com.app.couple.CoupleRepository;
import com.app.media.MediaType;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Escopo da porta na exclusao de conta (epico 9, US-007 / E9.2): apaga as notificacoes em que o
 * usuario e destinatario <b>ou</b> ator - o nome do ator aparece na UI do outro membro e e dado
 * pessoal dele. As notificacoes do casal que nao envolvem o usuario ficam.
 */
@DataJpaTest
class NotificationFacadeTest {

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private CoupleRepository coupleRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void deleteUserDataRemovesNotificationsWhereTheUserIsRecipientOrActor() {
		NotificationFacade facade = new NotificationFacade(notificationRepository);
		Couple couple = coupleRepository
			.save(new Couple(UUID.randomUUID(), "NFC" + UUID.randomUUID().toString().substring(0, 4)));
		UUID leaving = UUID.randomUUID();
		UUID partner = UUID.randomUUID();
		UUID stranger = UUID.randomUUID();

		Notification asRecipient = notificationRepository.save(new Notification(couple.getId(), leaving,
				NotificationType.MATCH, 603L, MediaType.MOVIE, "The Matrix", partner));
		Notification asActor = notificationRepository.save(new Notification(couple.getId(), partner,
				NotificationType.MATCH, 603L, MediaType.MOVIE, "The Matrix", leaving));
		Notification unrelated = notificationRepository.save(new Notification(couple.getId(), partner,
				NotificationType.NO_MATCH, 550L, MediaType.MOVIE, "Fight Club", stranger));
		entityManager.flush();

		facade.deleteUserData(leaving);
		entityManager.flush();
		entityManager.clear();

		assertThat(notificationRepository.findById(asRecipient.getId())).isEmpty();
		assertThat(notificationRepository.findById(asActor.getId())).isEmpty();
		assertThat(notificationRepository.findById(unrelated.getId()))
			.as("notificacao que nao envolve o usuario nao e dado pessoal dele")
			.isPresent();
	}
}
