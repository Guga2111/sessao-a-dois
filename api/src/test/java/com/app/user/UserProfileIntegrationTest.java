package com.app.user;

import com.app.auth.AuthCookieService;
import com.app.security.JwtService;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O que os testes de unidade da US-005 nao alcancam: que o valor novo aparece em
 * {@code GET /api/auth/me} na requisicao seguinte e que <b>o MESMO cookie de sessao continua
 * valendo depois da troca de e-mail</b> - o access token carrega {@code userId}, nunca o e-mail.
 * Por isso este teste autentica com um cookie {@code access_token} de verdade (emitido pelo
 * {@code JwtService}) em vez do post-processor {@code authentication(...)}, que curto-circuitaria
 * exatamente o que se quer provar.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserProfileIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtService jwtService;

	private RequestPostProcessor sessionOf(UUID userId) {
		Cookie cookie = new Cookie(AuthCookieService.ACCESS_TOKEN_COOKIE, jwtService.generateToken(userId));
		return request -> {
			request.setCookies(cookie);
			return request;
		};
	}

	@Test
	void theNewValuesShowUpInAuthMeAndTheSessionSurvivesTheEmailChange() throws Exception {
		String oldEmail = "antigo-" + UUID.randomUUID() + "@example.com";
		String newEmail = "novo-" + UUID.randomUUID() + "@example.com";
		User user = userRepository.save(new User("Ana", oldEmail, "hashed-password"));
		RequestPostProcessor session = sessionOf(user.getId());

		mockMvc.perform(get("/api/auth/me").with(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.email").value(oldEmail))
			.andExpect(jsonPath("$.user.name").value("Ana"));

		mockMvc.perform(patch("/api/user/me")
				.with(csrf())
				.with(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Ana Paula\",\"email\":\"" + newEmail + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(newEmail));

		// Mesmo cookie, emitido antes da troca: a sessao nao cai (a US-006 e o caso oposto).
		mockMvc.perform(get("/api/auth/me").with(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.email").value(newEmail))
			.andExpect(jsonPath("$.user.name").value("Ana Paula"));

		assertThat(userRepository.findByEmail(newEmail)).isPresent();
		assertThat(userRepository.findByEmail(oldEmail)).isEmpty();
	}

	@Test
	void anEmailAlreadyTakenByAnotherAccountIsRejectedWithConflictAndChangesNothing() throws Exception {
		String takenEmail = "ocupado-" + UUID.randomUUID() + "@example.com";
		userRepository.save(new User("Bia", takenEmail, "hashed-password"));
		String ownEmail = "propria-" + UUID.randomUUID() + "@example.com";
		User user = userRepository.save(new User("Ana", ownEmail, "hashed-password"));

		mockMvc.perform(patch("/api/user/me")
				.with(csrf())
				.with(sessionOf(user.getId()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Ana Paula\",\"email\":\"" + takenEmail + "\"}"))
			.andExpect(status().isConflict());

		User reloaded = userRepository.findById(user.getId()).orElseThrow();
		assertThat(reloaded.getEmail()).isEqualTo(ownEmail);
		assertThat(reloaded.getName()).isEqualTo("Ana");
	}
}
