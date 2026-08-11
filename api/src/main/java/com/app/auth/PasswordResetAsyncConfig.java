package com.app.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Executor dedicado ao despacho de esqueci-minha-senha (US-006): o handler HTTP nao
 * pode bloquear esperando o provedor de e-mail, entao o envio roda neste pool proprio,
 * separado de qualquer outro @Async que a aplicacao venha a ter.
 */
@Configuration
@EnableAsync
public class PasswordResetAsyncConfig {

	public static final String TASK_EXECUTOR_BEAN_NAME = "passwordResetTaskExecutor";

	@Bean(TASK_EXECUTOR_BEAN_NAME)
	public TaskExecutor passwordResetTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(50);
		executor.setThreadNamePrefix("password-reset-");
		executor.initialize();
		return executor;
	}
}
