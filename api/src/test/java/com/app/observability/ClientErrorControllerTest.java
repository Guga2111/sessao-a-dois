package com.app.observability;

import com.app.security.ClientIpResolver;
import com.app.security.JwtService;
import com.app.security.RateLimitProperties;
import com.app.security.RateLimitService;
import com.app.security.SecurityAuditLogger;
import com.app.security.SecurityConfig;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobre {@code POST /api/client-errors} (US-003, Epico 12). SecurityConfig
 * entra via @Import (mesmo motivo de HealthControllerTest: sem ela o slice
 * usaria a cadeia de seguranca padrao do Spring Boot, e "requisicao anonima
 * aceita" nao provaria nada). ClientErrorLogger entra real (nao mockado) para
 * que o teste de log verifique a linha de verdade, no mesmo espirito de como
 * os outros testes tratam SecurityAuditLogger.
 */
@WebMvcTest(ClientErrorController.class)
@Import({ SecurityConfig.class, ClientIpResolver.class, RateLimitService.class, RateLimitProperties.class,
	SecurityAuditLogger.class, ClientErrorLogger.class })
class ClientErrorControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private JwtService jwtService;

	private ListAppender<ILoggingEvent> logAppender;
	private Logger rootLogger;

	@BeforeEach
	void setUp() {
		logAppender = new ListAppender<>();
		logAppender.start();
		rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		rootLogger.addAppender(logAppender);
	}

	@AfterEach
	void tearDown() {
		rootLogger.detachAppender(logAppender);
	}

	@Test
	void reportingAClientErrorReturnsNoContent() throws Exception {
		mockMvc.perform(post("/api/client-errors")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"algo quebrou no render\",\"stack\":\"Error: boom\","
						+ "\"route\":\"/hub\",\"userAgent\":\"Mozilla/5.0\"}"))
			.andExpect(status().isNoContent())
			.andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEmpty());
	}

	@Test
	void messageAboveLimitIsRejectedWithoutEchoingTheBody() throws Exception {
		String tooLong = "x".repeat(501);

		String body = mockMvc.perform(post("/api/client-errors")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"" + tooLong + "\"}"))
			.andExpect(status().isBadRequest())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(body).doesNotContain(tooLong);
	}

	@Test
	void anonymousRequestIsAcceptedAndNeverConsultsTheAccessToken() throws Exception {
		mockMvc.perform(post("/api/client-errors")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"tela branca\"}"))
			.andExpect(status().isNoContent());

		verifyNoInteractions(jwtService);
	}

	@Test
	void reportedErrorIsLoggedWithTheCorrelationId() throws Exception {
		mockMvc.perform(post("/api/client-errors")
				.header("X-Request-Id", "corr-abc-123")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"algo quebrou\",\"route\":\"/hub\"}"))
			.andExpect(status().isNoContent());

		boolean found = logAppender.list.stream()
			.map(ILoggingEvent::getFormattedMessage)
			.anyMatch(line -> line.contains("event=client_error") && line.contains("correlationId=corr-abc-123")
					&& line.contains("route=\"/hub\"") && line.contains("message=\"algo quebrou\""));

		assertThat(found).isTrue();
	}
}
