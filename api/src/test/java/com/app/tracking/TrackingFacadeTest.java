package com.app.tracking;

import com.app.couple.Couple;
import com.app.couple.CoupleRepository;
import com.app.media.MediaType;
import com.app.user.User;
import com.app.user.UserRepository;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Escopo da porta na exclusao de conta (epico 9, US-007): {@code deleteUserData} apaga APENAS os
 * {@code user_review} do usuario. Roda contra repositorios reais (nao mocks) porque o que precisa
 * ser provado e o que sobra no banco depois - em especial que o {@code media_track} do casal
 * continua la (D13: ele pertence ao {@code couple_id}, e o historico do ex-parceiro).
 */
@DataJpaTest
class TrackingFacadeTest {

	@Autowired
	private MediaTrackRepository mediaTrackRepository;

	@Autowired
	private UserReviewRepository userReviewRepository;

	@Autowired
	private CoupleRepository coupleRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EntityManager entityManager;

	private TrackingFacade facade() {
		return new TrackingFacade(mediaTrackRepository, userReviewRepository);
	}

	private User newUser(String name) {
		return userRepository.save(new User(name, name.toLowerCase() + "-" + UUID.randomUUID() + "@example.com",
				"hashed-password"));
	}

	@Test
	void deleteUserDataRemovesOnlyTheUsersReviewsAndNeverTheCouplesTracks() {
		Couple couple = coupleRepository.save(new Couple(UUID.randomUUID(), "TFC" + UUID.randomUUID().toString().substring(0, 4)));
		User leaving = newUser("Ana");
		User staying = newUser("Bruno");

		MediaTrack track = mediaTrackRepository
			.save(new MediaTrack(couple.getId(), 603L, MediaType.MOVIE, MediaStatus.WATCHED));
		userReviewRepository.save(new UserReview(track, leaving, 5, "amei"));
		userReviewRepository.save(new UserReview(track, staying, 4, "gostei"));
		entityManager.flush();

		assertThat(userReviewRepository.count()).isEqualTo(2);

		facade().deleteUserData(leaving.getId());
		entityManager.flush();
		entityManager.clear();

		assertThat(userReviewRepository.findByMediaTrackIdAndUserId(track.getId(), leaving.getId())).isEmpty();
		assertThat(userReviewRepository.findByMediaTrackIdAndUserId(track.getId(), staying.getId())).isPresent();
		assertThat(mediaTrackRepository.findById(track.getId()))
			.as("o titulo rastreado pertence ao casal, nao ao usuario - a porta nao pode toca-lo")
			.isPresent();
	}
}
