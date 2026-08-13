package com.app;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Executor dedicado a checagem de banco de {@code GET /api/health} (US-002,
 * Epico 12): pool fixo e pequeno de threads daemon, para que um banco
 * pendurado nao acumule thread nova a cada probe (30s do container + 5min do
 * monitor externo) e nao atrase o shutdown da JVM.
 */
@Configuration
public class HealthCheckExecutorConfig {

	public static final String EXECUTOR_BEAN_NAME = "healthCheckExecutor";

	@Bean(EXECUTOR_BEAN_NAME)
	ExecutorService healthCheckExecutor() {
		AtomicInteger threadCount = new AtomicInteger();
		ThreadFactory daemonThreadFactory = runnable -> {
			Thread thread = new Thread(runnable, "health-check-" + threadCount.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		};
		return Executors.newFixedThreadPool(2, daemonThreadFactory);
	}
}
