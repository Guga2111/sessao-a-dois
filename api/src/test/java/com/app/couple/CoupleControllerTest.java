package com.app.couple;

import com.app.security.ClientIpResolver;
import com.app.security.JwtService;
import com.app.security.RateLimitProperties;
import com.app.security.RateLimitService;
import com.app.security.SecurityAuditLogger;
import com.app.security.SecurityConfig;
import com.app.user.User;
import com.app.user.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CoupleController.class)
@Import({ SecurityConfig.class, ClientIpResolver.class, RateLimitService.class, RateLimitProperties.class,
	SecurityAuditLogger.class, CoupleResponseMapper.class })
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
		mockMvc.perform(post("/api/couple").with(csrf()))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void createsCoupleForAuthenticatedUser() throws Exception {
		UUID userId = UUID.randomUUID();
		Couple couple = new Couple(userId, "ABC234");
		when(coupleService.createCouple(userId)).thenReturn(couple);

		mockMvc.perform(post("/api/couple")
				.with(csrf())
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
				.with(csrf())
				.with(authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("usuario ja pertence a um casal"));
	}

	@Test
	void returnsCurrentCoupleWithPartner() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID partnerId = UUID.randomUUID();
		Couple couple = new Couple(userId, null);
		couple.setUser2Id(partnerId);
		User partner = new User("Bruno", "bruno@example.com", "hashed-password");
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.of(couple));
		when(userRepository.findById(eq(partnerId))).thenReturn(Optional.of(partner));

		mockMvc.perform(get("/api/couple/me")
				.with(authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.inviteCode").value(org.hamcrest.Matchers.nullValue()))
			.andExpect(jsonPath("$.partner.name").value("Bruno"))
			.andExpect(jsonPath("$.partner.email").doesNotExist());
	}

	@Test
	void returnsNotFoundWhenUserHasNoCouple() throws Exception {
		UUID userId = UUID.randomUUID();
		when(coupleService.getCurrentCouple(userId)).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/couple/me")
				.with(authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()))))
			.andExpect(status().isNotFound());
	}

	@Test
	void joinsCoupleWithValidInviteCode() throws Exception {
		UUID user1Id = UUID.randomUUID();
		UUID user2Id = UUID.randomUUID();
		Couple couple = new Couple(user1Id, null);
		couple.setUser2Id(user2Id);
		User partner = new User("Ana", "ana@example.com", "hashed-password");
		when(coupleService.joinCouple(eq(user2Id), eq("ABC234"))).thenReturn(couple);
		when(userRepository.findById(eq(user1Id))).thenReturn(Optional.of(partner));

		mockMvc.perform(post("/api/couple/join")
				.with(csrf())
				.with(authentication(new UsernamePasswordAuthenticationToken(user2Id, null, List.of())))
				.contentType("application/json")
				.content("{\"inviteCode\":\"ABC234\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.inviteCode").value(org.hamcrest.Matchers.nullValue()))
			.andExpect(jsonPath("$.partner.name").value("Ana"))
			.andExpect(jsonPath("$.partner.email").doesNotExist());
	}

	@Test
	void returnsGoneForExpiredInviteCode() throws Exception {
		UUID userId = UUID.randomUUID();
		when(coupleService.joinCouple(eq(userId), eq("ABC234")))
			.thenThrow(new InviteCodeExpiredException());

		mockMvc.perform(post("/api/couple/join")
				.with(csrf())
				.with(authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of())))
				.contentType("application/json")
				.content("{\"inviteCode\":\"ABC234\"}"))
			.andExpect(status().isGone())
			.andExpect(jsonPath("$.message").value("codigo de convite expirado"));
	}

	@Test
	void returnsNotFoundForUnknownInviteCode() throws Exception {
		UUID userId = UUID.randomUUID();
		when(coupleService.joinCouple(eq(userId), eq("NOPE")))
			.thenThrow(new InviteCodeNotFoundException());

		mockMvc.perform(post("/api/couple/join")
				.with(csrf())
				.with(authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of())))
				.contentType("application/json")
				.content("{\"inviteCode\":\"NOPE\"}"))
			.andExpect(status().isNotFound());
	}

	@Test
	void rejectsSelfJoinWithBadRequest() throws Exception {
		UUID userId = UUID.randomUUID();
		when(coupleService.joinCouple(eq(userId), eq("ABC234")))
			.thenThrow(new CannotJoinOwnCoupleException());

		mockMvc.perform(post("/api/couple/join")
				.with(csrf())
				.with(authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of())))
				.contentType("application/json")
				.content("{\"inviteCode\":\"ABC234\"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsJoinWhenCoupleAlreadyFull() throws Exception {
		UUID userId = UUID.randomUUID();
		when(coupleService.joinCouple(eq(userId), eq("ABC234")))
			.thenThrow(new CoupleAlreadyFullException());

		mockMvc.perform(post("/api/couple/join")
				.with(csrf())
				.with(authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of())))
				.contentType("application/json")
				.content("{\"inviteCode\":\"ABC234\"}"))
			.andExpect(status().isConflict());
	}

	@Test
	void rejectsJoinWhenUserAlreadyPaired() throws Exception {
		UUID userId = UUID.randomUUID();
		when(coupleService.joinCouple(eq(userId), eq("ABC234")))
			.thenThrow(new UserAlreadyInCoupleException());

		mockMvc.perform(post("/api/couple/join")
				.with(csrf())
				.with(authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of())))
				.contentType("application/json")
				.content("{\"inviteCode\":\"ABC234\"}"))
			.andExpect(status().isConflict());
	}

	@Test
	void regeneratesInviteCodeForCreator() throws Exception {
		UUID userId = UUID.randomUUID();
		Couple couple = new Couple(userId, "NEW0001");
		when(coupleService.regenerateInviteCode(userId)).thenReturn(couple);

		mockMvc.perform(post("/api/couple/invite-code/regenerate")
				.with(csrf())
				.with(authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.inviteCode").value("NEW0001"));
	}

	@Test
	void rejectsRegenerateWhenCoupleAlreadyPaired() throws Exception {
		UUID userId = UUID.randomUUID();
		when(coupleService.regenerateInviteCode(userId)).thenThrow(new CoupleAlreadyFullException());

		mockMvc.perform(post("/api/couple/invite-code/regenerate")
				.with(csrf())
				.with(authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()))))
			.andExpect(status().isConflict());
	}

	@Test
	void rejectsRegenerateWhenUserIsNotCreator() throws Exception {
		UUID userId = UUID.randomUUID();
		when(coupleService.regenerateInviteCode(userId)).thenThrow(new NotCoupleCreatorException());

		mockMvc.perform(post("/api/couple/invite-code/regenerate")
				.with(csrf())
				.with(authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()))))
			.andExpect(status().isForbidden());
	}

	@Test
	void rejectsRegenerateWhenUserHasNoCouple() throws Exception {
		UUID userId = UUID.randomUUID();
		when(coupleService.regenerateInviteCode(userId)).thenThrow(new CoupleNotFoundException());

		mockMvc.perform(post("/api/couple/invite-code/regenerate")
				.with(csrf())
				.with(authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()))))
			.andExpect(status().isNotFound());
	}
}
