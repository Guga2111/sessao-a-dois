package com.app.notification;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Porta de {@code com.app.notification} para as demais features (epico 9, US-007).
 *
 * Como a {@code MatchFacade}, nasce com um unico metodo por causa da regra de dependencia entre
 * features - {@code com.app.user} nao pode importar {@code NotificationRepository} nem a entidade.
 * Os pontos de ENTRADA de notificacao continuam sendo {@code NotificationService.notifyCouple}/
 * {@code notifyRatingRequest}; esta porta e so o caminho de saida do dado pessoal.
 */
@Component
public class NotificationFacade {

	private final NotificationRepository notificationRepository;

	public NotificationFacade(NotificationRepository notificationRepository) {
		this.notificationRepository = notificationRepository;
	}

	/**
	 * Exclusao de conta: apaga as notificacoes em que o usuario e destinatario ou ator (E9.2). As
	 * notificacoes do casal que nao envolvem o usuario permanecem.
	 */
	@Transactional
	public void deleteUserData(UUID userId) {
		notificationRepository.deleteByRecipientUserIdOrActorUserId(userId);
	}
}
