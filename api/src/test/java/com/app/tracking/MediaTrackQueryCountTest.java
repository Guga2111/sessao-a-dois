package com.app.tracking;

import com.app.couple.Couple;
import com.app.couple.CoupleRepository;
import com.app.media.MediaType;
import com.app.user.User;
import com.app.user.UserRepository;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the N+1/lazy-collection fixes from US-004 against a real (H2, create-drop) Hibernate
 * session, not just by static reading: the query count for /api/tracking must NOT scale with the
 * number of tracks, and the paginated endpoint must paginate at the DB (no HHH000104 in-memory
 * pagination warning).
 */
@SpringBootTest
@AutoConfigureMockMvc
class MediaTrackQueryCountTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CoupleRepository coupleRepository;

	@Autowired
	private MediaTrackRepository mediaTrackRepository;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	private Statistics statistics;
	private ListAppender<ILoggingEvent> logAppender;
	private Logger rootLogger;

	@BeforeEach
	void setUp() {
		statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.setStatisticsEnabled(true);

		logAppender = new ListAppender<>();
		logAppender.start();
		rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		rootLogger.addAppender(logAppender);
	}

	@AfterEach
	void tearDown() {
		rootLogger.detachAppender(logAppender);
	}

	@Test
	void keysEndpointExecutesConstantQueryCountRegardlessOfTrackCount() throws Exception {
		long queriesForFewTracks = queryCountForKeysListing(3);
		long queriesForManyTracks = queryCountForKeysListing(30);

		assertThat(queriesForManyTracks).isEqualTo(queriesForFewTracks);
	}

	@Test
	void pagedListEndpointPaginatesInDatabaseWithoutInMemoryFetchWarning() throws Exception {
		User user1 = persistUser();
		User user2 = persistUser();
		Couple couple = persistCouple(user1, user2);

		int totalTracks = 25;
		int pageSize = 20;
		for (int i = 0; i < totalTracks; i++) {
			MediaTrack track = new MediaTrack(couple.getId(), 1000L + i, MediaType.MOVIE, MediaStatus.WATCHED);
			track.getReviews().add(new UserReview(track, user1, 5, "Otimo"));
			track.getReviews().add(new UserReview(track, user2, 4, "Bom"));
			mediaTrackRepository.save(track);
		}

		statistics.clear();
		logAppender.list.clear();

		mockMvc.perform(get("/api/tracking")
				.param("status", "WATCHED")
				.param("page", "0")
				.param("size", String.valueOf(pageSize))
				.with(authentication(new UsernamePasswordAuthenticationToken(user1.getId(), null, List.of()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(pageSize))
			.andExpect(jsonPath("$.totalElements").value(totalTracks));

		boolean hasInMemoryPaginationWarning = logAppender.list.stream()
			.anyMatch(event -> event.getFormattedMessage().contains("HHH000104"));
		assertThat(hasInMemoryPaginationWarning).isFalse();
	}

	/**
	 * Depois da US-003 o couple_id e um UUID solto: os membros do casal e os nomes deles passaram a
	 * ser resolvidos por chamada explicita, e o risco e resolve-los por track. A contagem constante
	 * de queries e o que prova que a resolucao continua sendo uma por request.
	 */
	@Test
	void pagedListEndpointExecutesConstantQueryCountRegardlessOfTrackCount() throws Exception {
		// Os dois passam de uma pagina (21 e 40 com size=20) de proposito: abaixo do tamanho da
		// pagina o Spring Data pula a query de count e a comparacao mediria isso, nao o N+1.
		long queriesForFewTracks = queryCountForPagedListing(21);
		long queriesForManyTracks = queryCountForPagedListing(40);

		assertThat(queriesForManyTracks).isEqualTo(queriesForFewTracks);
	}

	private long queryCountForPagedListing(int trackCount) throws Exception {
		User user1 = persistUser();
		User user2 = persistUser();
		Couple couple = persistCouple(user1, user2);

		for (int i = 0; i < trackCount; i++) {
			MediaTrack track = new MediaTrack(couple.getId(), 3000L + i, MediaType.MOVIE, MediaStatus.WATCHED);
			track.getReviews().add(new UserReview(track, user1, 5, "Otimo"));
			track.getReviews().add(new UserReview(track, user2, 4, "Bom"));
			mediaTrackRepository.save(track);
		}

		statistics.clear();

		mockMvc.perform(get("/api/tracking")
				.param("status", "WATCHED")
				.param("page", "0")
				.param("size", "20")
				.with(authentication(new UsernamePasswordAuthenticationToken(user1.getId(), null, List.of()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(20));

		return statistics.getPrepareStatementCount();
	}

	private long queryCountForKeysListing(int trackCount) throws Exception {
		User user1 = persistUser();
		User user2 = persistUser();
		Couple couple = persistCouple(user1, user2);

		for (int i = 0; i < trackCount; i++) {
			MediaTrack track = new MediaTrack(couple.getId(), 2000L + i, MediaType.MOVIE, MediaStatus.WATCHED);
			track.getReviews().add(new UserReview(track, user1, 5, "Otimo"));
			track.getReviews().add(new UserReview(track, user2, 4, "Bom"));
			mediaTrackRepository.save(track);
		}

		statistics.clear();

		mockMvc.perform(get("/api/tracking/keys")
				.with(authentication(new UsernamePasswordAuthenticationToken(user1.getId(), null, List.of()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(trackCount));

		return statistics.getPrepareStatementCount();
	}

	private User persistUser() {
		return userRepository.save(new User("User " + UUID.randomUUID(), "user-" + UUID.randomUUID() + "@example.com",
				"hash"));
	}

	private Couple persistCouple(User user1, User user2) {
		Couple couple = new Couple(user1.getId(), "QC" + UUID.randomUUID().toString().substring(0, 6));
		couple.setUser2Id(user2.getId());
		return coupleRepository.save(couple);
	}
}
