package com.app.match;

import com.app.couple.Couple;
import com.app.couple.CoupleRepository;
import com.app.media.MediaType;
import com.app.tracking.MediaStatus;
import com.app.tracking.MediaTrack;
import com.app.tracking.MediaTrackRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class MatchLikeRepositoryTest {

	@Autowired
	private MatchLikeRepository matchLikeRepository;

	@Autowired
	private MatchRejectRepository matchRejectRepository;

	@Autowired
	private MediaTrackRepository mediaTrackRepository;

	@Autowired
	private CoupleRepository coupleRepository;

	private Couple persistedCouple() {
		return coupleRepository.save(new Couple(UUID.randomUUID(), "MTC" + UUID.randomUUID().toString().substring(0, 4)));
	}

	@Test
	void findByCoupleIdAndUserIdAndTmdbIdFindsOwnLike() {
		Couple couple = persistedCouple();
		UUID userId = UUID.randomUUID();
		matchLikeRepository.save(new MatchLike(couple.getId(), userId, 603L, MediaType.MOVIE));

		assertThat(matchLikeRepository.findByCoupleIdAndUserIdAndTmdbId(couple.getId(), userId, 603L)).isPresent();
		assertThat(matchLikeRepository.findByCoupleIdAndUserIdAndTmdbId(couple.getId(), UUID.randomUUID(), 603L))
			.isEmpty();
		assertThat(matchLikeRepository.findByCoupleIdAndUserIdAndTmdbId(couple.getId(), userId, 999L)).isEmpty();
	}

	@Test
	void findFirstByCoupleIdAndTmdbIdAndUserIdNotFindsPartnerLikeButNotOwn() {
		Couple couple = persistedCouple();
		UUID userId = UUID.randomUUID();
		UUID partnerId = UUID.randomUUID();
		matchLikeRepository.save(new MatchLike(couple.getId(), userId, 603L, MediaType.MOVIE));
		matchLikeRepository.save(new MatchLike(couple.getId(), partnerId, 603L, MediaType.MOVIE));

		assertThat(matchLikeRepository.findFirstByCoupleIdAndTmdbIdAndUserIdNot(couple.getId(), 603L, userId))
			.isPresent()
			.get()
			.extracting(MatchLike::getUserId)
			.isEqualTo(partnerId);

		assertThat(matchLikeRepository.findFirstByCoupleIdAndTmdbIdAndUserIdNot(couple.getId(), 603L, partnerId))
			.isPresent()
			.get()
			.extracting(MatchLike::getUserId)
			.isEqualTo(userId);
	}

	@Test
	void findFirstByCoupleIdAndTmdbIdAndUserIdNotIsEmptyWhenOnlyUserLiked() {
		Couple couple = persistedCouple();
		UUID userId = UUID.randomUUID();
		matchLikeRepository.save(new MatchLike(couple.getId(), userId, 603L, MediaType.MOVIE));

		assertThat(matchLikeRepository.findFirstByCoupleIdAndTmdbIdAndUserIdNot(couple.getId(), 603L, userId))
			.isEmpty();
	}

	@Test
	void duplicateLikeFromSameUserForSameTitleViolatesUniqueConstraint() {
		Couple couple = persistedCouple();
		UUID userId = UUID.randomUUID();
		matchLikeRepository.saveAndFlush(new MatchLike(couple.getId(), userId, 603L, MediaType.MOVIE));

		assertThatThrownBy(() ->
			matchLikeRepository.saveAndFlush(new MatchLike(couple.getId(), userId, 603L, MediaType.MOVIE))
		).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void findPendingForUserIsEmptyWhenPartnerHasNoLikes() {
		Couple couple = persistedCouple();
		UUID userId = UUID.randomUUID();

		Page<MatchLike> pending = matchLikeRepository.findPendingForUser(couple.getId(), userId, PageRequest.of(0, 10));

		assertThat(pending.getContent()).isEmpty();
	}

	@Test
	void findPendingForUserExcludesTitlesAlreadyRejectedByCurrentUser() {
		Couple couple = persistedCouple();
		UUID userId = UUID.randomUUID();
		UUID partnerId = UUID.randomUUID();
		matchLikeRepository.save(new MatchLike(couple.getId(), partnerId, 603L, MediaType.MOVIE));
		matchRejectRepository.save(new MatchReject(couple.getId(), userId, 603L, MediaType.MOVIE));

		Page<MatchLike> pending = matchLikeRepository.findPendingForUser(couple.getId(), userId, PageRequest.of(0, 10));

		assertThat(pending.getContent()).isEmpty();
	}

	@Test
	void findPendingForUserExcludesTitlesAlreadyTrackedAsMediaTrack() {
		Couple couple = persistedCouple();
		UUID userId = UUID.randomUUID();
		UUID partnerId = UUID.randomUUID();
		matchLikeRepository.save(new MatchLike(couple.getId(), partnerId, 603L, MediaType.MOVIE));
		mediaTrackRepository.save(new MediaTrack(couple.getId(), 603L, MediaType.MOVIE, MediaStatus.WANT_TO_SEE));

		Page<MatchLike> pending = matchLikeRepository.findPendingForUser(couple.getId(), userId, PageRequest.of(0, 10));

		assertThat(pending.getContent()).isEmpty();
	}

	@Test
	void findPendingForUserReturnsPartnerLikeStillPending() {
		Couple couple = persistedCouple();
		UUID userId = UUID.randomUUID();
		UUID partnerId = UUID.randomUUID();
		MatchLike partnerLike = matchLikeRepository.save(new MatchLike(couple.getId(), partnerId, 603L, MediaType.MOVIE));

		Page<MatchLike> pending = matchLikeRepository.findPendingForUser(couple.getId(), userId, PageRequest.of(0, 10));

		assertThat(pending.getContent()).hasSize(1);
		assertThat(pending.getContent().get(0).getId()).isEqualTo(partnerLike.getId());
	}
}
