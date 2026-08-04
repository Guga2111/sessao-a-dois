package com.app.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;

/**
 * Resolve/cria buckets bucket4j por chave arbitraria (IP, e-mail, usuario)
 * e responde se a requisicao correspondente pode seguir. Uma unica instancia
 * de cache serve todos os limites da aplicacao - cada chamador e responsavel
 * por compor uma chave unica por endpoint (ex.: "login:ip:1.2.3.4").
 *
 * Buckets sem uso por INACTIVITY_EVICTION sao removidos periodicamente para
 * que uma chave nunca reusada (ex.: IP de um atacante que desiste) nao vaze
 * memoria indefinidamente.
 */
@Service
public class RateLimitService {

	private static final Duration INACTIVITY_EVICTION = Duration.ofHours(2);

	private final Map<String, BucketHolder> buckets = new ConcurrentHashMap<>();

	public RateLimitResult tryConsume(String key, int capacity, Duration window) {
		BucketHolder holder = buckets.computeIfAbsent(key, k -> new BucketHolder(newBucket(capacity, window)));
		holder.touch();

		ConsumptionProbe probe = holder.bucket().tryConsumeAndReturnRemaining(1);
		if (probe.isConsumed()) {
			return RateLimitResult.ofAllowed();
		}
		long retryAfterSeconds = Math.max(1, (long) Math.ceil(probe.getNanosToWaitForRefill() / 1_000_000_000.0));
		return RateLimitResult.ofBlocked(retryAfterSeconds);
	}

	@Scheduled(fixedRate = 30 * 60 * 1000)
	void evictInactiveBuckets() {
		Instant threshold = Instant.now().minus(INACTIVITY_EVICTION);
		buckets.entrySet().removeIf(entry -> entry.getValue().lastAccess().isBefore(threshold));
	}

	private static Bucket newBucket(int capacity, Duration window) {
		Bandwidth limit = Bandwidth.classic(capacity, Refill.intervally(capacity, window));
		return Bucket.builder().addLimit(limit).build();
	}

	public record RateLimitResult(boolean allowed, long retryAfterSeconds) {

		static RateLimitResult ofAllowed() {
			return new RateLimitResult(true, 0);
		}

		static RateLimitResult ofBlocked(long retryAfterSeconds) {
			return new RateLimitResult(false, retryAfterSeconds);
		}
	}

	private static final class BucketHolder {

		private final Bucket bucket;
		private volatile Instant lastAccess;

		BucketHolder(Bucket bucket) {
			this.bucket = bucket;
			this.lastAccess = Instant.now();
		}

		Bucket bucket() {
			return bucket;
		}

		Instant lastAccess() {
			return lastAccess;
		}

		void touch() {
			this.lastAccess = Instant.now();
		}
	}
}
