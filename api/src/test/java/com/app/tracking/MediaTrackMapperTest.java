package com.app.tracking;

import com.app.couple.Couple;
import com.app.media.MediaType;
import com.app.user.User;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MediaTrackMapperTest {

	private final MediaTrackMapper mapper = new MediaTrackMapper();

	@Test
	void memberIdsIncludesBothMembersWhenCoupleIsComplete() {
		UUID user1Id = UUID.randomUUID();
		UUID user2Id = UUID.randomUUID();
		Couple couple = new Couple(user1Id, "ABC234");
		couple.setUser2Id(user2Id);

		assertThat(MediaTrackMapper.memberIds(couple)).containsExactly(user1Id, user2Id);
	}

	@Test
	void memberIdsSkipsNullSecondMember() {
		UUID user1Id = UUID.randomUUID();
		Couple couple = new Couple(user1Id, "ABC234");

		assertThat(MediaTrackMapper.memberIds(couple)).containsExactly(user1Id);
	}

	@Test
	void toResponseUsesResolvedNamesForBothMembersReviews() {
		UUID user1Id = UUID.randomUUID();
		UUID user2Id = UUID.randomUUID();
		Couple couple = new Couple(user1Id, "ABC234");
		couple.setUser2Id(user2Id);

		User user1 = new User("Ana", "ana@example.com", "hash");
		ReflectionTestUtils.setField(user1, "id", user1Id);
		User user2 = new User("Bob", "bob@example.com", "hash");
		ReflectionTestUtils.setField(user2, "id", user2Id);

		MediaTrack track = new MediaTrack(couple, 603L, MediaType.MOVIE, MediaStatus.WATCHED);
		track.getReviews().add(new UserReview(track, user1, 5, "Ana adorou"));
		track.getReviews().add(new UserReview(track, user2, 2, "Bob nem tanto"));

		Map<UUID, String> userNames = new HashMap<>();
		userNames.put(user1Id, "Ana");
		userNames.put(user2Id, "Bob");

		MediaTrackResponse response = mapper.toResponse(track, userNames);

		assertThat(response.tmdbId()).isEqualTo(603L);
		List<ReviewDto> reviews = response.reviews();
		assertThat(reviews).hasSize(2);
		assertThat(reviews)
			.anySatisfy(review -> {
				assertThat(review.userId()).isEqualTo(user1Id);
				assertThat(review.userName()).isEqualTo("Ana");
				assertThat(review.rating()).isEqualTo(5);
				assertThat(review.opinion()).isEqualTo("Ana adorou");
			})
			.anySatisfy(review -> {
				assertThat(review.userId()).isEqualTo(user2Id);
				assertThat(review.userName()).isEqualTo("Bob");
				assertThat(review.rating()).isEqualTo(2);
				assertThat(review.opinion()).isEqualTo("Bob nem tanto");
			});
	}

	@Test
	void toResponseOmitsReviewForMemberWithoutOne() {
		UUID user1Id = UUID.randomUUID();
		Couple couple = new Couple(user1Id, "ABC234");

		MediaTrack track = new MediaTrack(couple, 603L, MediaType.MOVIE, MediaStatus.WANT_TO_SEE);

		MediaTrackResponse response = mapper.toResponse(track, Map.of(user1Id, "Ana"));

		assertThat(response.reviews()).hasSize(1);
		ReviewDto review = response.reviews().get(0);
		assertThat(review.userId()).isEqualTo(user1Id);
		assertThat(review.userName()).isEqualTo("Ana");
		assertThat(review.rating()).isNull();
		assertThat(review.opinion()).isNull();
	}

	@Test
	void toResponseFallsBackToNullNameWhenNotResolved() {
		UUID user1Id = UUID.randomUUID();
		Couple couple = new Couple(user1Id, "ABC234");
		MediaTrack track = new MediaTrack(couple, 603L, MediaType.MOVIE, MediaStatus.WANT_TO_SEE);

		MediaTrackResponse response = mapper.toResponse(track, Map.of());

		assertThat(response.reviews().get(0).userName()).isNull();
	}

	@Test
	void toResponseCopiesPersistedMetadataFromEntity() {
		UUID user1Id = UUID.randomUUID();
		Couple couple = new Couple(user1Id, "ABC234");
		MediaTrack track = new MediaTrack(couple, 603L, MediaType.MOVIE, MediaStatus.WANT_TO_SEE);
		track.setTitle("Matrix");
		track.setPosterUrl("/poster.jpg");
		track.setReleaseYear(1999);

		MediaTrackResponse response = mapper.toResponse(track, Map.of(user1Id, "Ana"));

		assertThat(response.title()).isEqualTo("Matrix");
		assertThat(response.posterUrl()).isEqualTo("/poster.jpg");
		assertThat(response.releaseYear()).isEqualTo(1999);
	}

	@Test
	void toResponseAllowsNullMetadataForPreV4Rows() {
		UUID user1Id = UUID.randomUUID();
		Couple couple = new Couple(user1Id, "ABC234");
		MediaTrack track = new MediaTrack(couple, 603L, MediaType.MOVIE, MediaStatus.WANT_TO_SEE);

		MediaTrackResponse response = mapper.toResponse(track, Map.of(user1Id, "Ana"));

		assertThat(response.title()).isNull();
		assertThat(response.posterUrl()).isNull();
		assertThat(response.releaseYear()).isNull();
	}
}
