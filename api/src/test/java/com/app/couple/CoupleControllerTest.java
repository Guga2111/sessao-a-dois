package com.app.couple;

import com.app.security.JwtService;
import com.app.security.SecurityConfig;
import com.app.user.User;
import com.app.user.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CoupleController.class)
@Import(SecurityConfig.class)
class CoupleControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CoupleService coupleService;

	@MockitoBean
	private UserRepository userRepository;

	@MockitoBean
	private JwtService jwtService;

	@Test
	void deniesAccessWithoutAuthentication() throws Exception {
		mockMvc.perform(post("/api/couple"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void createsCoupleForAuthenticatedUser() throws Exception {
		UUID userId = UUID.randomUUID();
		Couple couple = new Couple(userId, "ABC234");
		when(coupleService.createCouple(userId)).thenReturn(couple);

		mockMvc.perform(post("/api/couple")
				.with(authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.inviteCode").value("ABC234"))
			.andExpect(jsonPath("$.partner").doesNotExist());
	}

	@Test
	void rejectsSecondCoupleCreationWithConflict() throws Exception {
		UUID userId = UUID.randomUUID();
		when(coupleService.createCouple(userId)).thenThrow(new UserAlreadyInCoupleException());

		mockMvc.perform(post("/api/couple")
				.with(authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("usuario ja pertence a um casal"));
	}

	@Test
	void returnsCurrentCoupleWithPartner() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID partnerId = UUID.randomUUID();
		Couple couple = new Couple(userId, "ABC234");
		couple.setUser2Id(partnerId);
		User partner = new User("Bruno", "bruno@example.com", "hashed-password");
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.of(couple));
		when(userRepository.findById(eq(partnerId))).thenReturn(Optional.of(partner));

		mockMvc.perform(get("/api/couple/me")
				.with(authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.inviteCode").value("ABC234"))
			.andExpect(jsonPath("$.partner.name").value("Bruno"));
	}

	@Test
	void returnsNotFoundWhenUserHasNoCouple() throws Exception {
		UUID userId = UUID.randomUUID();
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/couple/me")
				.with(authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()))))
			.andExpect(status().isNotFound());
	}
}
