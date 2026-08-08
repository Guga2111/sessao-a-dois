package com.app.auth;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O que os testes de unidade da US-006 nao alcancam: que a troca de senha realmente derruba a
 * sessao no servidor (E9.8). Por isso este teste usa os cookies de verdade emitidos pelo login -
 * o post-processor {@code authentication(...)} injetaria o principal direto e passaria mesmo se
 * o token/refresh tivesse continuado valendo.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordChangeIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void changingThePasswordDropsEverySessionAndOnlyTheNewPasswordLogsInAgain() throws Exception {
		String email = "senha-" + UUID.randomUUID() + "@example.com";
		register(email, "senha-antiga-1");

		MvcResult login = login(email, "senha-antiga-1").andExpect(status().isOk()).andReturn();
		Cookie accessCookie = login.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
		Cookie refreshCookie = login.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE);
		assertThat(accessCookie).isNotNull();
		assertThat(refreshCookie).isNotNull();

		mockMvc.perform(get("/api/auth/me").cookie(accessCookie)).andExpect(status().isOk());

		MvcResult change = mockMvc.perform(put("/api/auth/password")
				.with(csrf())
				.cookie(accessCookie)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"currentPassword":"senha-antiga-1","newPassword":"senha-nova-12"}
					"""))
			.andExpect(status().isNoContent())
			.andReturn();

		List<String> setCookies = change.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
		assertThat(setCookies).hasSize(2).allMatch(c -> c.contains("Max-Age=0"));

		// O refresh anterior morreu junto com a familia inteira (revokeFamily).
		mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
			.andExpect(status().isUnauthorized());

		login(email, "senha-antiga-1").andExpect(status().isUnauthorized());
		login(email, "senha-nova-12").andExpect(status().isOk());
	}

	@Test
	void aWrongCurrentPasswordChangesNothing() throws Exception {
		String email = "senha-errada-" + UUID.randomUUID() + "@example.com";
		register(email, "senha-antiga-1");

		MvcResult login = login(email, "senha-antiga-1").andExpect(status().isOk()).andReturn();
		Cookie accessCookie = login.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
		Cookie refreshCookie = login.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE);

		mockMvc.perform(put("/api/auth/password")
				.with(csrf())
				.cookie(accessCookie)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"currentPassword":"nao-e-essa","newPassword":"senha-nova-12"}
					"""))
			.andExpect(status().isUnauthorized());

		// Nem a senha, nem as sessoes: o refresh emitido antes continua rotacionando.
		mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie)).andExpect(status().isNoContent());
		login(email, "senha-antiga-1").andExpect(status().isOk());
	}

	private void register(String email, String password) throws Exception {
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Ana\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
			.andExpect(status().isCreated());
	}

	private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
	}
}
