package com.app.email;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

class ResendEmailSenderTest {

	private EmailProperties properties() {
		EmailProperties properties = new EmailProperties();
		properties.setProvider("resend");
		properties.setApiKey("test-api-key");
		properties.setFrom("naoresponda@sessaoadois.luisgosampaio.com");
		properties.setConnectTimeout(Duration.ofSeconds(3));
		properties.setReadTimeout(Duration.ofSeconds(5));
		return properties;
	}

	private RestClient.Builder builder() {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		return RestClient.builder()
			.baseUrl("https://api.resend.com")
			.requestFactory(requestFactory)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-api-key");
	}

	@Test
	void sendPasswordReset_success_postsToResendWithAuthHeader() {
		RestClient.Builder builder = builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		RestClient restClient = builder.build();

		server.expect(requestTo("https://api.resend.com/emails"))
			.andExpect(method(POST))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-api-key"))
			.andRespond(withSuccess("{\"id\":\"abc\"}", MediaType.APPLICATION_JSON));

		ResendEmailSender sender = new ResendEmailSender(restClient, properties());

		sender.sendPasswordReset("user@example.com", "Fulano", "https://app.example.com/redefinir-senha?token=abc");

		server.verify();
	}

	@Test
	void sendPasswordChangedNotice_clientError_throwsEmailDeliveryException() {
		RestClient.Builder builder = builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		RestClient restClient = builder.build();

		server.expect(requestTo("https://api.resend.com/emails"))
			.andExpect(method(POST))
			.andRespond(withStatus(BAD_REQUEST).body("{\"message\":\"invalid from\"}")
				.contentType(MediaType.APPLICATION_JSON));

		ResendEmailSender sender = new ResendEmailSender(restClient, properties());

		assertThatThrownBy(() -> sender.sendPasswordChangedNotice("user@example.com", "Fulano"))
			.isInstanceOf(EmailDeliveryException.class);

		server.verify();
	}

	@Test
	void sendPasswordReset_serverError_throwsEmailDeliveryException() {
		RestClient.Builder builder = builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		RestClient restClient = builder.build();

		server.expect(requestTo("https://api.resend.com/emails"))
			.andExpect(method(POST))
			.andRespond(withServerError());

		ResendEmailSender sender = new ResendEmailSender(restClient, properties());

		assertThatThrownBy(
			() -> sender.sendPasswordReset("user@example.com", "Fulano", "https://app.example.com/redefinir-senha?token=abc"))
			.isInstanceOf(EmailDeliveryException.class);

		server.verify();
	}

	@Test
	void sendPasswordReset_timeout_throwsEmailDeliveryException() {
		RestClient.Builder builder = builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		RestClient restClient = builder.build();

		server.expect(requestTo("https://api.resend.com/emails"))
			.andExpect(method(POST))
			.andRespond(request -> {
				throw new SocketTimeoutException("timeout");
			});

		ResendEmailSender sender = new ResendEmailSender(restClient, properties());

		assertThatThrownBy(
			() -> sender.sendPasswordReset("user@example.com", "Fulano", "https://app.example.com/redefinir-senha?token=abc"))
			.isInstanceOf(EmailDeliveryException.class);

		server.verify();
	}
}
