package com.app.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre as tres formas de X-Forwarded-For: ausente (cai em getRemoteAddr()),
 * IP unico, e cadeia de IPs (usa o primeiro).
 */
class ClientIpResolverTest {

	private final ClientIpResolver resolver = new ClientIpResolver();

	@Test
	void fallsBackToRemoteAddrWhenHeaderMissing() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("192.168.1.10");

		assertThat(resolver.resolve(request)).isEqualTo("192.168.1.10");
	}

	@Test
	void fallsBackToRemoteAddrWhenHeaderBlank() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("192.168.1.10");
		request.addHeader("X-Forwarded-For", "   ");

		assertThat(resolver.resolve(request)).isEqualTo("192.168.1.10");
	}

	@Test
	void usesSingleIpFromHeader() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("192.168.1.10");
		request.addHeader("X-Forwarded-For", "203.0.113.5");

		assertThat(resolver.resolve(request)).isEqualTo("203.0.113.5");
	}

	@Test
	void usesFirstIpFromChain() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("192.168.1.10");
		request.addHeader("X-Forwarded-For", "203.0.113.5, 70.41.3.18, 150.172.238.178");

		assertThat(resolver.resolve(request)).isEqualTo("203.0.113.5");
	}
}
