package com.app.user;

import com.app.couple.Couple;
import com.app.couple.CoupleRepository;
import com.app.match.MatchLike;
import com.app.match.MatchLikeRepository;
import com.app.match.MatchReject;
import com.app.match.MatchRejectRepository;
import com.app.media.MediaType;
import com.app.notification.Notification;
import com.app.notification.NotificationRepository;
import com.app.notification.NotificationType;
import com.app.tracking.MediaStatus;
import com.app.tracking.MediaTrack;
import com.app.tracking.MediaTrackRepository;
import com.app.tracking.UserReview;
import com.app.tracking.UserReviewRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O contrato completo de {@code DELETE /api/user/me} (epico 9, US-007) com repositorios reais: o que
 * some, o que FICA, e o que volta a ser possivel depois. Os testes de unidade cobrem a orquestracao
 * (ordem das portas, senha, rate limit); o que so aparece aqui e a interacao entre as features - em
 * especial que o historico do casal sobrevive a saida de um dos dois (D13).
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserDeletionIntegrationTest {

	private static final String SENHA = "senha-atual-123";

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
	private MatchRejectRepository matchRejectRepository;

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private User newUser(String name) {
		return userRepository.save(new User(name, name.toLowerCase() + "-" + UUID.randomUUID() + "@example.com",
				passwordEncoder.encode(SENHA)));
	}

	private Couple pairedCouple(User user1, User user2, String inviteCode) {
		Couple couple = new Couple(user1.getId(), inviteCode);
		couple.setUser2Id(user2.getId());
		return coupleRepository.save(couple);
	}

	private RequestPostProcessor as(User user) {
		return authentication(new UsernamePasswordAuthenticationToken(user.getId(), null, List.of()));
	}

	private void seedCoupleData(Couple couple, User leaving, User partner) {
		MediaTrack track = new MediaTrack(couple.getId(), 603L, MediaType.MOVIE, MediaStatus.WATCHED);
		track.setTitle("The Matrix");
		mediaTrackRepository.save(track);
		userReviewRepository.save(new UserReview(track, leaving, 5, "amei"));
		userReviewRepository.save(new UserReview(track, partner, 4, "gostei"));

		matchLikeRepository.save(new MatchLike(couple.getId(), leaving.getId(), 604L, MediaType.MOVIE));
		matchLikeRepository.save(new MatchLike(couple.getId(), partner.getId(), 604L, MediaType.MOVIE));
		matchRejectRepository.save(new MatchReject(couple.getId(), leaving.getId(), 550L, MediaType.MOVIE));

		notificationRepository.save(new Notification(couple.getId(), leaving.getId(), NotificationType.MATCH, 603L,
				MediaType.MOVIE, "The Matrix", partner.getId()));
		notificationRepository.save(new Notification(couple.getId(), partner.getId(), NotificationType.MATCH, 603L,
				MediaType.MOVIE, "The Matrix", leaving.getId()));
	}

	private String passwordBody(String password) {
		return "{\"password\":\"" + password + "\"}";
	}

	@Test
	void deletingTheAccountRemovesThePersonalDataAndKeepsTheCouplesHistory() throws Exception {
		User ana = newUser("Ana");
		User bruno = newUser("Bruno");
		Couple couple = pairedCouple(ana, bruno, "DEL0001");
		seedCoupleData(couple, ana, bruno);

		long tracksBefore = mediaTrackRepository.count();

		mockMvc.perform(delete("/api/user/me")
				.with(csrf())
				.with(as(ana))
				.contentType("application/json")
				.content(passwordBody(SENHA)))
			.andExpect(status().isNoContent());

		assertThat(userRepository.findById(ana.getId())).isEmpty();
		assertThat(userRepository.findById(bruno.getId())).isPresent();

		// O que some: o dado pessoal de quem saiu.
		assertThat(userReviewRepository.findAll())
			.noneMatch(review -> review.getUser().getId().equals(ana.getId()));
		assertThat(matchLikeRepository.findByCoupleIdAndUserIdAndTmdbId(couple.getId(), ana.getId(), 604L)).isEmpty();
		assertThat(matchRejectRepository.findByCoupleIdAndUserIdAndTmdbId(couple.getId(), ana.getId(), 550L)).isEmpty();
		assertThat(notificationRepository.findAll())
			.noneMatch(n -> ana.getId().equals(n.getRecipientUserId()) || ana.getId().equals(n.getActorUserId()));

		// O que fica: o historico do casal e o dado do ex-parceiro.
		assertThat(mediaTrackRepository.count())
			.as("o media_track pertence ao couple_id, nao ao usuario (D13)")
			.isEqualTo(tracksBefore);
		assertThat(userReviewRepository.findAll()).anyMatch(review -> review.getUser().getId().equals(bruno.getId()));
		assertThat(matchLikeRepository.findByCoupleIdAndUserIdAndTmdbId(couple.getId(), bruno.getId(), 604L))
			.isPresent();
		assertThat(coupleRepository.findById(couple.getId()))
			.as("nenhuma linha de couples e apagada - a dissolucao e UPDATE dissolved_at")
			.isPresent();
		assertThat(coupleRepository.findById(couple.getId()).orElseThrow().isActive()).isFalse();
	}

	@Test
	void aWrongPasswordDeletesNothing() throws Exception {
		User ana = newUser("Ana");
		Couple couple = coupleRepository.save(new Couple(ana.getId(), "DEL0002"));

		mockMvc.perform(delete("/api/user/me")
				.with(csrf())
				.with(as(ana))
				.contentType("application/json")
				.content(passwordBody("senha-errada-999")))
			.andExpect(status().isUnauthorized());

		assertThat(userRepository.findById(ana.getId())).isPresent();
		assertThat(coupleRepository.findById(couple.getId()).orElseThrow().isActive()).isTrue();
	}

	@Test
	void deletingWorksForSomeoneWithoutACouple() throws Exception {
		User solo = newUser("Solo");

		mockMvc.perform(delete("/api/user/me")
				.with(csrf())
				.with(as(solo))
				.contentType("application/json")
				.content(passwordBody(SENHA)))
			.andExpect(status().isNoContent());

		assertThat(userRepository.findById(solo.getId())).isEmpty();
	}

	/** E9.13: o e-mail volta a ser cadastravel e a conta nova nao herda nada. */
	@Test
	void theEmailIsFreeAgainAndTheNewAccountInheritsNothing() throws Exception {
		User ana = newUser("Ana");
		User bruno = newUser("Bruno");
		pairedCouple(ana, bruno, "DEL0003");
		String email = ana.getEmail();

		mockMvc.perform(delete("/api/user/me")
				.with(csrf())
				.with(as(ana))
				.contentType("application/json")
				.content(passwordBody(SENHA)))
			.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/auth/login")
				.with(csrf())
				.contentType("application/json")
				.content("{\"email\":\"" + email + "\",\"password\":\"" + SENHA + "\"}"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/auth/register")
				.with(csrf())
				.contentType("application/json")
				.content("{\"name\":\"Ana de novo\",\"email\":\"" + email + "\",\"password\":\"" + SENHA + "\"}"))
			.andExpect(status().isCreated());

		User recadastrada = userRepository.findByEmail(email).orElseThrow();
		assertThat(recadastrada.getId()).isNotEqualTo(ana.getId());
		assertThat(coupleRepository.findActiveByUserId(recadastrada.getId()))
			.as("conta nova, sem casal e sem historico")
			.isEmpty();
		assertThat(userReviewRepository.findAll())
			.noneMatch(review -> review.getUser().getId().equals(recadastrada.getId()));
	}

	@Test
	void theExPartnerCanFormANewCoupleAfterTheDeletion() throws Exception {
		User ana = newUser("Ana");
		User bruno = newUser("Bruno");
		pairedCouple(ana, bruno, "DEL0004");

		mockMvc.perform(delete("/api/user/me")
				.with(csrf())
				.with(as(ana))
				.contentType("application/json")
				.content(passwordBody(SENHA)))
			.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/couple").with(csrf()).with(as(bruno)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.inviteCode").isNotEmpty());
	}

}
