package com.app.couple;

import com.app.match.MatchLike;
import com.app.match.MatchLikeRepository;
import com.app.media.MediaType;
import com.app.notification.Notification;
import com.app.notification.NotificationRepository;
import com.app.notification.NotificationType;
import com.app.tracking.MediaStatus;
import com.app.tracking.MediaTrack;
import com.app.tracking.MediaTrackRepository;
import com.app.tracking.UserReview;
import com.app.tracking.UserReviewRepository;
import com.app.user.User;
import com.app.user.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova o contrato da dissolucao (epico 9, US-004) de ponta a ponta, com repositorios reais: depois de
 * {@code DELETE /api/couple/me}, nenhum endpoint de {@code couple}/{@code tracking}/{@code match}/
 * {@code notification} devolve dado do casal antigo para nenhum dos dois ex-membros - <b>e nenhuma
 * linha e apagada</b> (D13: dissolver, nao apagar). Os testes de unidade cobrem o servico e o
 * controller; o que so aparece aqui e a interacao entre as features.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CoupleDissolutionIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CoupleRepository coupleRepository;

	@Autowired
	private MediaTrackRepository mediaTrackRepository;

	@Autowired
	private UserReviewRepository userReviewRepository;

	@Autowired
	private MatchLikeRepository matchLikeRepository;

	@Autowired
	private NotificationRepository notificationRepository;

	private User newUser(String name) {
		return userRepository.save(new User(name, name.toLowerCase() + "-" + UUID.randomUUID() + "@example.com",
				"hashed-password"));
	}

	private Couple pairedCouple(User user1, User user2, String inviteCode) {
		Couple couple = new Couple(user1.getId(), inviteCode);
		couple.setUser2Id(user2.getId());
		return coupleRepository.save(couple);
	}

	private RequestPostProcessor as(User user) {
		return authentication(new UsernamePasswordAuthenticationToken(user.getId(), null, List.of()));
	}

	/** Um track WATCHED com review dos dois, um like pendente e uma notificacao MATCH por membro. */
	private void seedCoupleData(Couple couple, User user1, User user2) {
		MediaTrack track = new MediaTrack(couple.getId(), 603L, MediaType.MOVIE, MediaStatus.WATCHED);
		track.setTitle("The Matrix");
		mediaTrackRepository.save(track);
		userReviewRepository.save(new UserReview(track, user1, 5, "otimo"));
		userReviewRepository.save(new UserReview(track, user2, 4, "bom"));

		MatchLike like = new MatchLike(couple.getId(), user1.getId(), 604L, MediaType.MOVIE);
		like.setTitle("The Matrix Reloaded");
		matchLikeRepository.save(like);

		notificationRepository.save(new Notification(couple.getId(), user1.getId(), NotificationType.MATCH, 603L,
				MediaType.MOVIE, "The Matrix", user2.getId()));
		notificationRepository.save(new Notification(couple.getId(), user2.getId(), NotificationType.MATCH, 603L,
				MediaType.MOVIE, "The Matrix", user1.getId()));
	}

	@Test
	void dissolvingHidesEveryCoupleScopedEndpointFromBothExMembersWithoutDeletingAnyRow() throws Exception {
		User ana = newUser("Ana");
		User bruno = newUser("Bruno");
		Couple couple = pairedCouple(ana, bruno, "DIS0001");
		seedCoupleData(couple, ana, bruno);

		long tracksBefore = mediaTrackRepository.count();
		long reviewsBefore = userReviewRepository.count();
		long notificationsBefore = notificationRepository.count();

		mockMvc.perform(get("/api/tracking").param("status", "WATCHED").with(as(ana)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1));
		mockMvc.perform(get("/api/notifications").with(as(bruno)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1));
		mockMvc.perform(get("/api/notifications/unread-count").with(as(bruno)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.count").value(1));

		// A acao e unilateral: so o Bruno chama, e o vinculo acaba para os dois.
		mockMvc.perform(delete("/api/couple/me").with(csrf()).with(as(bruno)))
			.andExpect(status().isNoContent());

		for (User exMember : List.of(ana, bruno)) {
			mockMvc.perform(get("/api/couple/me").with(as(exMember))).andExpect(status().isNotFound());
			mockMvc.perform(get("/api/tracking").param("status", "WATCHED").with(as(exMember)))
				.andExpect(status().isNotFound());
			mockMvc.perform(get("/api/tracking/keys").with(as(exMember))).andExpect(status().isNotFound());
			mockMvc.perform(get("/api/match/pending").with(as(exMember))).andExpect(status().isNotFound());
			mockMvc.perform(get("/api/notifications").with(as(exMember)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));
			mockMvc.perform(get("/api/notifications/unread-count").with(as(exMember)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.count").value(0));
		}

		assertThat(mediaTrackRepository.count()).isEqualTo(tracksBefore);
		assertThat(userReviewRepository.count()).isEqualTo(reviewsBefore);
		assertThat(notificationRepository.count()).isEqualTo(notificationsBefore);
		assertThat(coupleRepository.findById(couple.getId())).isPresent();
	}

	@Test
	void bothExMembersCanFormANewCoupleAfterDissolving() throws Exception {
		User ana = newUser("Ana");
		User bruno = newUser("Bruno");
		Couple couple = pairedCouple(ana, bruno, "DIS0002");

		mockMvc.perform(delete("/api/couple/me").with(csrf()).with(as(ana)))
			.andExpect(status().isNoContent());

		User carla = newUser("Carla");
		mockMvc.perform(post("/api/couple").with(csrf()).with(as(ana)))
			.andExpect(status().isCreated());
		String anaInvite = coupleRepository.findActiveByUserId(ana.getId()).orElseThrow().getInviteCode();
		mockMvc.perform(post("/api/couple/join")
				.with(csrf())
				.with(as(carla))
				.contentType("application/json")
				.content("{\"inviteCode\":\"" + anaInvite + "\"}"))
			.andExpect(status().isOk());

		// O Bruno, que nao pediu a dissolucao, tambem esta livre.
		mockMvc.perform(post("/api/couple").with(csrf()).with(as(bruno)))
			.andExpect(status().isCreated());

		assertThat(coupleRepository.findById(couple.getId()).orElseThrow().isActive()).isFalse();
	}

	@Test
	void theInviteCodeOfADissolvedCoupleNeverLetsAnyoneIn() throws Exception {
		User ana = newUser("Ana");
		Couple couple = coupleRepository.save(new Couple(ana.getId(), "DIS0003"));

		mockMvc.perform(delete("/api/couple/me").with(csrf()).with(as(ana)))
			.andExpect(status().isNoContent());

		assertThat(coupleRepository.findById(couple.getId()).orElseThrow().getInviteCode()).isNull();

		User carla = newUser("Carla");
		mockMvc.perform(post("/api/couple/join")
				.with(csrf())
				.with(as(carla))
				.contentType("application/json")
				.content("{\"inviteCode\":\"DIS0003\"}"))
			.andExpect(status().isNotFound());
	}

	/**
	 * Linha legada: um casal marcado como dissolvido que ainda tem {@code invite_code} preenchido (estado
	 * que o {@code dissolve()} nao produz, mas que um dado antigo ou um UPDATE manual produziria). Quem
	 * barra aqui e o filtro do {@code findActiveByInviteCode}, nao a limpeza do codigo.
	 */
	@Test
	void aLegacyDissolvedRowWithAnInviteCodeStillCannotBeJoined() throws Exception {
		User ana = newUser("Ana");
		Couple couple = new Couple(ana.getId(), "DIS0004");
		ReflectionTestUtils.setField(couple, "dissolvedAt", Instant.now());
		coupleRepository.save(couple);

		User carla = newUser("Carla");
		mockMvc.perform(post("/api/couple/join")
				.with(csrf())
				.with(as(carla))
				.contentType("application/json")
				.content("{\"inviteCode\":\"DIS0004\"}"))
			.andExpect(status().isNotFound());
	}

	/**
	 * Cenario da E9.20: com um casal dissolvido E um ativo, o usuario tem DUAS linhas em {@code couples}.
	 * Uma consulta sem o filtro de dissolucao devolveria {@code Optional} sobre duas linhas e quebraria em
	 * {@code IncorrectResultSizeDataAccessException} (500) - por isso as duas rotas abaixo sao testadas.
	 */
	@Test
	void aUserWithOneDissolvedAndOneActiveCoupleIsServedNormally() throws Exception {
		User ana = newUser("Ana");
		User bruno = newUser("Bruno");
		pairedCouple(ana, bruno, "DIS0005");

		mockMvc.perform(delete("/api/couple/me").with(csrf()).with(as(ana)))
			.andExpect(status().isNoContent());
		mockMvc.perform(post("/api/couple").with(csrf()).with(as(ana)))
			.andExpect(status().isCreated());

		assertThat(coupleRepository.findAll().stream()
			.filter(c -> ana.getId().equals(c.getUser1Id()) || ana.getId().equals(c.getUser2Id()))
			.count()).isEqualTo(2);

		mockMvc.perform(get("/api/couple/me").with(as(ana)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.partner").doesNotExist());
		mockMvc.perform(post("/api/couple/invite-code/regenerate").with(csrf()).with(as(ana)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.inviteCode").isNotEmpty());
	}
}
