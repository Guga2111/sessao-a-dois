package com.app.user;

import com.app.auth.RefreshToken;
import com.app.auth.RefreshTokenRepository;
import com.app.couple.Couple;
import com.app.couple.CoupleRepository;
import com.app.media.MediaType;
import com.app.tracking.MediaStatus;
import com.app.tracking.MediaTrack;
import com.app.tracking.MediaTrackRepository;
import com.app.tracking.UserReview;
import com.app.tracking.UserReviewRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Integridade referencial da exclusao de conta (epico 9, US-007) contra o schema REAL das
 * migrations - o mesmo arranjo do {@code FlywayMigrationTest}: {@code replace = NONE} +
 * {@code spring.flyway.enabled=true} + {@code ddl-auto=validate}, apontando para {@code ${DB_URL}}.
 *
 * <p><b>Local/sandbox</b> (sem {@code DB_URL}): cai em H2 {@code MODE=PostgreSQL}, que exercita a
 * sintaxe mas <b>nao</b> e prova suficiente para a auto-FK {@code fk_refresh_token_replaced_by}.
 * <b>CI</b>: {@code DB_URL} aponta para o {@code postgres:17-alpine} de verdade, e e la que este
 * teste vale como prova. Os demais testes de exclusao rodam sobre o schema gerado das entidades
 * (create-drop), onde nenhuma FK das migrations existe.
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
	"spring.datasource.url=${DB_URL:jdbc:h2:mem:migrations;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE}",
	"spring.datasource.username=${DB_USER:sa}",
	"spring.datasource.password=${DB_PASSWORD:}",
	"spring.flyway.enabled=true",
	"spring.jpa.hibernate.ddl-auto=validate"
})
class UserDeletionIntegrityTest {

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
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private User newUser(String name) {
		return userRepository.save(new User(name, name.toLowerCase() + "-" + UUID.randomUUID() + "@example.com",
				passwordEncoder.encode(SENHA)));
	}

	private UUID saveToken(User user, UUID replacedById) {
		RefreshToken token = new RefreshToken(user.getId(), "hash-" + UUID.randomUUID(),
				Instant.now().plusSeconds(3600), "UA", "127.0.0.1");
		token.setReplacedById(replacedById);
		return refreshTokenRepository.save(token).getId();
	}

	/**
	 * Cadeia de rotacao de 3 tokens encadeados por {@code replaced_by_id}: apagar linha a linha
	 * ({@code deleteAll(entidades)}) violaria a auto-FK assim que o token ainda referenciado saisse
	 * primeiro. Num {@code DELETE} unico o Postgres so checa a integridade no fim do statement.
	 */
	@Test
	void deletingAnAccountWithARotationChainViolatesNoForeignKey() {
		User ana = newUser("Ana");
		User bruno = newUser("Bruno");
		Couple couple = new Couple(ana.getId(), "INT" + UUID.randomUUID().toString().substring(0, 5));
		couple.setUser2Id(bruno.getId());
		coupleRepository.save(couple);

		MediaTrack track = mediaTrackRepository
			.save(new MediaTrack(couple.getId(), 603L, MediaType.MOVIE, MediaStatus.WATCHED));
		userReviewRepository.save(new UserReview(track, ana, 5, "amei"));
		userReviewRepository.save(new UserReview(track, bruno, 4, "gostei"));

		UUID tail = saveToken(ana, null);
		UUID middle = saveToken(ana, tail);
		saveToken(ana, middle);

		assertThatCode(() -> userDeletionService.deleteAccount(ana.getId(), new DeleteAccountRequest(SENHA)))
			.doesNotThrowAnyException();

		assertThat(userRepository.findById(ana.getId())).isEmpty();
		assertThat(refreshTokenRepository.findAll()).noneMatch(token -> token.getUserId().equals(ana.getId()));
		assertThat(userReviewRepository.findAll()).noneMatch(review -> review.getUser().getId().equals(ana.getId()));
		assertThat(mediaTrackRepository.findById(track.getId()))
			.as("media_track referencia couples e continua no banco")
			.isPresent();
		assertThat(coupleRepository.findById(couple.getId()))
			.as("nenhuma linha de couples e apagada - notification.couple_id e NOT NULL com FK para ela")
			.isPresent();
	}
}
