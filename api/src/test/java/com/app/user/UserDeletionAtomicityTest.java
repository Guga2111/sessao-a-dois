package com.app.user;

import com.app.couple.Couple;
import com.app.couple.CoupleRepository;
import com.app.media.MediaType;
import com.app.notification.NotificationFacade;
import com.app.tracking.MediaStatus;
import com.app.tracking.MediaTrack;
import com.app.tracking.MediaTrackRepository;
import com.app.tracking.UserReview;
import com.app.tracking.UserReviewRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * A exclusao roda em UMA transacao: se qualquer passo falhar no meio, nada fica meio-apagado.
 * O ponto de falha e forcado na penultima porta ({@code NotificationFacade}), depois de tres
 * exclusoes ja terem acontecido - e exatamente o estado intermediario que um rollback ausente
 * deixaria visivel.
 */
@SpringBootTest
class UserDeletionAtomicityTest {

	private static final String SENHA = "senha-atual-123";

	@Autowired
	private UserDeletionService userDeletionService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CoupleRepository coupleRepository;

	@Autowired
	private MediaTrackRepository mediaTrackRepository;

	@Autowired
	private UserReviewRepository userReviewRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@MockitoBean
	private NotificationFacade notificationFacade;

	@Test
	void aFailureInTheMiddleRollsTheWholeDeletionBack() {
		User ana = userRepository.save(new User("Ana", "ana-" + UUID.randomUUID() + "@example.com",
				passwordEncoder.encode(SENHA)));
		Couple couple = coupleRepository.save(new Couple(ana.getId(), "ATM" + UUID.randomUUID().toString().substring(0, 5)));
		MediaTrack track = mediaTrackRepository
			.save(new MediaTrack(couple.getId(), 603L, MediaType.MOVIE, MediaStatus.WATCHED));
		userReviewRepository.save(new UserReview(track, ana, 5, "amei"));

		doThrow(new IllegalStateException("falha simulada")).when(notificationFacade).deleteUserData(any(UUID.class));

		assertThatThrownBy(() -> userDeletionService.deleteAccount(ana.getId(), new DeleteAccountRequest(SENHA)))
			.isInstanceOf(IllegalStateException.class);

		assertThat(userRepository.findById(ana.getId()))
			.as("a conta continua intacta")
			.isPresent();
		assertThat(userReviewRepository.findAll())
			.as("a avaliacao ja apagada dentro da transacao volta com o rollback")
			.anyMatch(review -> review.getUser().getId().equals(ana.getId()));
		assertThat(coupleRepository.findById(couple.getId()).orElseThrow().isActive())
			.as("a dissolucao tambem e desfeita")
			.isTrue();
	}
}
