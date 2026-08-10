package com.app.email;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;

/**
 * Propriedades de e-mail transacional. Com app.email.provider=resend, apiKey e from
 * sao obrigatorias e a ausencia de qualquer uma delas falha o startup (fail-fast),
 * no mesmo padrao de com.app.media.TmdbConfig e com.app.security.JwtService. Com
 * provider=log nenhuma credencial e exigida. app.public-url e sempre validada como
 * URL absoluta, independente do provider, pois compoe o link enviado no e-mail.
 */
@Component
@ConfigurationProperties(prefix = "app.email")
public class EmailProperties {

	private static final String RESEND_PROVIDER = "resend";

	private String provider = "log";

	private String apiKey;

	private String from;

	private Duration connectTimeout = Duration.ofSeconds(3);

	private Duration readTimeout = Duration.ofSeconds(5);

	@Value("${app.public-url:https://sessaoadois.luisgosampaio.com}")
	private String publicUrl;

	@PostConstruct
	void validate() {
		if (RESEND_PROVIDER.equals(provider)) {
			if (!StringUtils.hasText(apiKey)) {
				throw new IllegalStateException(
					"RESEND_API_KEY nao configurada. Defina a variavel de ambiente RESEND_API_KEY antes de iniciar a aplicacao.");
			}
			if (!StringUtils.hasText(from)) {
				throw new IllegalStateException(
					"EMAIL_FROM nao configurada. Defina a variavel de ambiente EMAIL_FROM antes de iniciar a aplicacao.");
			}
		}
		validatePublicUrl();
	}

	private void validatePublicUrl() {
		if (!StringUtils.hasText(publicUrl)) {
			throw new IllegalStateException(
				"APP_PUBLIC_URL invalida: valor ausente. Defina a variavel de ambiente APP_PUBLIC_URL com uma URL absoluta (ex.: https://exemplo.com).");
		}
		boolean absolute;
		try {
			absolute = new URI(publicUrl).isAbsolute();
		}
		catch (URISyntaxException ex) {
			throw new IllegalStateException(
				"APP_PUBLIC_URL invalida: '" + publicUrl
					+ "' nao e uma URL valida. Defina a variavel de ambiente APP_PUBLIC_URL com uma URL absoluta (ex.: https://exemplo.com).",
				ex);
		}
		if (!absolute) {
			throw new IllegalStateException("APP_PUBLIC_URL invalida: '" + publicUrl
				+ "' nao e uma URL absoluta. Defina a variavel de ambiente APP_PUBLIC_URL com uma URL absoluta (ex.: https://exemplo.com).");
		}
	}

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public String getFrom() {
		return from;
	}

	public void setFrom(String from) {
		this.from = from;
	}

	public Duration getConnectTimeout() {
		return connectTimeout;
	}

	public void setConnectTimeout(Duration connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	public Duration getReadTimeout() {
		return readTimeout;
	}

	public void setReadTimeout(Duration readTimeout) {
		this.readTimeout = readTimeout;
	}

	public String getPublicUrl() {
		return publicUrl;
	}

	public void setPublicUrl(String publicUrl) {
		this.publicUrl = publicUrl;
	}
}
