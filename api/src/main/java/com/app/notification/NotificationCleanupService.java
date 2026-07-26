package com.app.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class NotificationCleanupService {

	private static final Logger log = LoggerFactory.getLogger(NotificationCleanupService.class);

	private static final int RETENTION_DAYS = 30;

	private final NotificationRepository notificationRepository;

	public NotificationCleanupService(NotificationRepository notificationRepository) {
		this.notificationRepository = notificationRepository;
	}

	@Scheduled(cron = "0 0 3 * * *")
	@Transactional
	public void expireOldNotifications() {
		LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
		long removed = notificationRepository.deleteByCreatedAtBefore(cutoff);
		log.info("Expired {} notifications older than {} days", removed, RETENTION_DAYS);
	}
}
