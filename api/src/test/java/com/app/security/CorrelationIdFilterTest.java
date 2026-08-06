package com.app.security;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cobre: geracao de um novo id quando X-Request-Id esta ausente, reuso do
 * header quando presente, devolucao do id num header de resposta, limpeza do
 * MDC ao final da requisicao (inclusive quando a cadeia lanca excecao - senao
 * o id vaza para a proxima requisicao na mesma thread do pool do Tomcat), e
 * que duas requisicoes concorrentes recebem ids distintos sem uma enxergar o
 * id da outra.
 */
class CorrelationIdFilterTest {

	private final CorrelationIdFilter filter = new CorrelationIdFilter();

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	@Test
	void generatesNewCorrelationIdWhenHeaderAbsent() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicReference<String> seenDuringChain = new AtomicReference<>();
		FilterChain chain = (req, res) -> seenDuringChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));

		filter.doFilter(request, response, chain);

		assertThat(seenDuringChain.get()).isNotBlank();
		assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo(seenDuringChain.get());
		assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
	}

	@Test
	void reusesIncomingRequestIdHeader() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(CorrelationIdFilter.HEADER_NAME, "incoming-id-123");
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicReference<String> seenDuringChain = new AtomicReference<>();
		FilterChain chain = (req, res) -> seenDuringChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));

		filter.doFilter(request, response, chain);

		assertThat(seenDuringChain.get()).isEqualTo("incoming-id-123");
		assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo("incoming-id-123");
	}

	@Test
	void clearsMdcEvenWhenChainThrows() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = (req, res) -> {
			throw new RuntimeException("boom");
		};

		assertThatThrownBy(() -> filter.doFilter(request, response, chain)).isInstanceOf(RuntimeException.class);
		assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
	}

	@Test
	void concurrentRequestsGetDistinctIdsWithoutLeakingAcrossThreads() throws Exception {
		int requestCount = 20;
		List<String> seenIds = new CopyOnWriteArrayList<>();
		CountDownLatch ready = new CountDownLatch(requestCount);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(requestCount);

		try {
			for (int i = 0; i < requestCount; i++) {
				pool.submit(() -> {
					try {
						MockHttpServletRequest request = new MockHttpServletRequest();
						MockHttpServletResponse response = new MockHttpServletResponse();
						FilterChain chain = (req, res) -> {
							String id = MDC.get(CorrelationIdFilter.MDC_KEY);
							ready.countDown();
							try {
								release.await();
							} catch (InterruptedException e) {
								Thread.currentThread().interrupt();
								throw new RuntimeException(e);
							}
							// A mesma thread nao deve ver o id de outra requisicao concorrente
							// atribuida a outra thread do pool nesse meio-tempo.
							assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo(id);
							seenIds.add(id);
						};
						filter.doFilter(request, response, chain);
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				});
			}
			ready.await(5, TimeUnit.SECONDS);
			release.countDown();
			pool.shutdown();
			assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
		} finally {
			pool.shutdownNow();
		}

		assertThat(seenIds).hasSize(requestCount);
		assertThat(seenIds).doesNotHaveDuplicates();
	}
}
