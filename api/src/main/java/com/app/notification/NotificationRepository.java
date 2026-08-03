package com.app.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

	Page<Notification> findByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId, Pageable pageable);

	long countByRecipientUserIdAndReadFalse(UUID recipientUserId);

	@Modifying
	@Query("DELETE FROM Notification n WHERE n.createdAt < :cutoff")
	int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);

	/**
	 * Idempotencia do pedido de avaliacao: escopado ao casal para nunca cruzar
	 * notificacoes de casais diferentes que rastreiam o mesmo tmdbId.
	 */
	boolean existsByRecipientUserIdAndCoupleIdAndTmdbIdAndTypeAndReadFalse(UUID recipientUserId, UUID coupleId,
			Long tmdbId, NotificationType type);

	/**
	 * Pedidos de avaliacao ainda pendentes para um destinatario num titulo do
	 * casal: usados para resolver automaticamente o pedido quando ele avalia.
	 * Escopado ao casal pelo mesmo motivo da query de idempotencia acima.
	 */
	List<Notification> findByRecipientUserIdAndCoupleIdAndTmdbIdAndTypeAndReadFalse(UUID recipientUserId,
			UUID coupleId, Long tmdbId, NotificationType type);

	/**
	 * Limpeza dos pedidos de avaliacao quando o titulo sai da lista do casal:
	 * escopada ao casal e ao tipo, para nunca apagar MATCH/NO_MATCH nem tocar em
	 * outro casal que rastreie o mesmo tmdbId.
	 */
	long deleteByCoupleIdAndTmdbIdAndType(UUID coupleId, Long tmdbId, NotificationType type);

	@Modifying
	@Query("UPDATE Notification n SET n.read = true WHERE n.recipientUserId = :recipientUserId AND n.read = false")
	int markAllAsReadByRecipientUserId(@Param("recipientUserId") UUID recipientUserId);
}
