package com.app.security;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.app.security.RateLimitService.RateLimitResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre o comportamento do bucket bucket4j por chave: liberacao dentro do
 * limite, bloqueio ao exceder, e liberacao apos a janela.
 */
class RateLimitServiceTest {

	private final RateLimitService service = new RateLimitService();

	@Test
	void allowsRequestsWithinCapacity() {
		String key = "test:within-capacity";

		for (int i = 0; i < 5; i++) {
			RateLimitResult result = service.tryConsume(key, 5, Duration.ofMinutes(1));
			assertThat(result.allowed()).isTrue();
		}
	}

	@Test
	void blocksWhenCapacityExceeded() {
		String key = "test:exceeded";

		for (int i = 0; i < 3; i++) {
			assertThat(service.tryConsume(key, 3, Duration.ofMinutes(1)).allowed()).isTrue();
		}

		RateLimitResult blocked = service.tryConsume(key, 3, Duration.ofMinutes(1));
		assertThat(blocked.allowed()).isFalse();
		assertThat(blocked.retryAfterSeconds()).isGreaterThan(0);
	}

	@Test
	void independentKeysHaveIndependentBuckets() {
		for (int i = 0; i < 2; i++) {
			assertThat(service.tryConsume("test:key-a", 2, Duration.ofMinutes(1)).allowed()).isTrue();
		}
		assertThat(service.tryConsume("test:key-a", 2, Duration.ofMinutes(1)).allowed()).isFalse();

		assertThat(service.tryConsume("test:key-b", 2, Duration.ofMinutes(1)).allowed()).isTrue();
	}

	@Test
	void allowsAgainAfterWindowElapses() throws InterruptedException {
		String key = "test:window-elapses";
		Duration window = Duration.ofMillis(200);

		assertThat(service.tryConsume(key, 1, window).allowed()).isTrue();
		assertThat(service.tryConsume(key, 1, window).allowed()).isFalse();

		Thread.sleep(300);

		assertThat(service.tryConsume(key, 1, window).allowed()).isTrue();
	}
}
