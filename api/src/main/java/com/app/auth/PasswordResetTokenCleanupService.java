package com.app.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class PasswordResetTokenCleanupService {

	private static final Logger log = LoggerFactory.getLogger(PasswordResetTokenCleanupService.class);

	private final PasswordResetTokenRepository passwordResetTokenRepository;

	public PasswordResetTokenCleanupService(PasswordResetTokenRepository passwordResetTokenRepository) {
		this.passwordResetTokenRepository = passwordResetTokenRepository;
	}

	@Scheduled(cron = "0 30 3 * * *")
	@Transactional
	public void removeExpiredOrUsedTokens() {
		int removed = passwordResetTokenRepository.deleteExpiredOrUsed(Instant.now());
		log.info("Removed {} expired or used password reset tokens", removed);
	}
}
