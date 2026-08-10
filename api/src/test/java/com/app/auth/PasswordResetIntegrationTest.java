package com.app.auth;

import com.app.user.User;
import com.app.user.UserRepository;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O que os testes de unidade da US-007 nao alcancam: que o reset realmente derruba a
 * sessao no servidor e que o token so funciona uma vez, contra o schema/servicos reais
 * (nao um AuthService mockado). O token em claro nunca sai do backend em produção (so
 * viaja no e-mail) - aqui e obtido chamando PasswordResetService.issueResetLink
 * diretamente, o unico jeito de um teste conhecer o valor sem ler e-mail nenhum.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordResetService passwordResetService;

	@Test
	void resettingThePasswordDropsEverySessionAndOnlyTheNewPasswordLogsInAgain() throws Exception {
		String email = "reset-" + UUID.randomUUID() + "@example.com";
		register(email, "senha-antiga-1");

		MvcResult login = login(email, "senha-antiga-1").andExpect(status().isOk()).andReturn();
		Cookie refreshCookie = login.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE);

		String rawToken = issueRawToken(email);

		mockMvc.perform(post("/api/auth/reset-password")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + rawToken + "\",\"newPassword\":\"senha-nova-12\"}"))
			.andExpect(status().isNoContent());

		// O refresh anterior morreu junto com a familia inteira (revokeFamily).
		mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie)).andExpect(status().isUnauthorized());

		login(email, "senha-antiga-1").andExpect(status().isUnauthorized());
		login(email, "senha-nova-12").andExpect(status().isOk());
	}

	@Test
	void presentingTheSameTokenTwiceIsRejectedTheSecondTime() throws Exception {
		String email = "reset-reuso-" + UUID.randomUUID() + "@example.com";
		register(email, "senha-antiga-1");
		String rawToken = issueRawToken(email);

		mockMvc.perform(post("/api/auth/reset-password")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + rawToken + "\",\"newPassword\":\"senha-nova-12\"}"))
			.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/auth/reset-password")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + rawToken + "\",\"newPassword\":\"outra-senha-12\"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void anUnknownTokenIsRejectedWithBadRequest() throws Exception {
		mockMvc.perform(post("/api/auth/reset-password")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"nao-existe-nunca\",\"newPassword\":\"senha-nova-12\"}"))
			.andExpect(status().isBadRequest());
	}

	private String issueRawToken(String email) {
		User user = userRepository.findByEmail(email).orElseThrow();
		String link = passwordResetService.issueResetLink(user);
		return link.substring(link.indexOf("token=") + "token=".length());
	}

	private void register(String email, String password) throws Exception {
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Ana\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
			.andExpect(status().isCreated());
	}

	private ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
	}
}
