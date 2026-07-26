package com.app.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

	Page<Notification> findByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId, Pageable pageable);

	long countByRecipientUserIdAndReadFalse(UUID recipientUserId);

	long deleteByCreatedAtBefore(LocalDateTime cutoff);

	@Modifying
	@Query("UPDATE Notification n SET n.read = true WHERE n.recipientUserId = :recipientUserId AND n.read = false")
	int markAllAsReadByRecipientUserId(@Param("recipientUserId") UUID recipientUserId);
}
