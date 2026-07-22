package com.app.tracking;

import com.app.media.MediaType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MediaTrackRepository extends JpaRepository<MediaTrack, UUID> {

	List<MediaTrack> findByCoupleIdAndStatus(UUID coupleId, MediaStatus status);

	List<MediaTrack> findByCoupleId(UUID coupleId);

	boolean existsByCoupleIdAndTmdbId(UUID coupleId, Long tmdbId);

	long countByCoupleIdAndStatusAndMediaType(UUID coupleId, MediaStatus status, MediaType mediaType);

	@Query("SELECT COALESCE(SUM(mt.runtime), 0) FROM MediaTrack mt "
		+ "WHERE mt.couple.id = :coupleId AND mt.status = :status AND mt.mediaType = :mediaType")
	int sumRuntimeByCoupleIdAndStatusAndMediaType(@Param("coupleId") UUID coupleId,
			@Param("status") MediaStatus status, @Param("mediaType") MediaType mediaType);

	@Query("SELECT COALESCE(SUM(mt.runtime), 0) FROM MediaTrack mt "
		+ "WHERE mt.couple.id = :coupleId AND mt.status = :status AND mt.mediaType = :mediaType "
		+ "AND EXTRACT(MONTH FROM mt.watchedDate) = :month AND EXTRACT(YEAR FROM mt.watchedDate) = :year")
	int sumRuntimeByCoupleIdAndStatusAndMediaTypeForMonth(@Param("coupleId") UUID coupleId,
			@Param("status") MediaStatus status, @Param("mediaType") MediaType mediaType,
			@Param("month") int month, @Param("year") int year);

	@Query("SELECT EXTRACT(MONTH FROM mt.watchedDate) AS month, COUNT(mt) AS total FROM MediaTrack mt "
		+ "WHERE mt.couple.id = :coupleId AND mt.status = :status AND EXTRACT(YEAR FROM mt.watchedDate) = :year "
		+ "GROUP BY EXTRACT(MONTH FROM mt.watchedDate)")
	List<MonthlyCount> countByCoupleIdAndStatusGroupedByMonth(@Param("coupleId") UUID coupleId,
			@Param("status") MediaStatus status, @Param("year") int year);

	@Query("SELECT g AS genreId, COUNT(g) AS total FROM MediaTrack mt JOIN mt.genreIds g "
		+ "WHERE mt.couple.id = :coupleId AND mt.status = :status GROUP BY g")
	List<GenreCount> countGenreOccurrencesByCoupleIdAndStatus(@Param("coupleId") UUID coupleId,
			@Param("status") MediaStatus status);

	interface MonthlyCount {
		Integer getMonth();

		Long getTotal();
	}

	interface GenreCount {
		Integer getGenreId();

		Long getTotal();
	}
}
