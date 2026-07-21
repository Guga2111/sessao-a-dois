package com.app.tracking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MediaTrackRepository extends JpaRepository<MediaTrack, UUID> {

	List<MediaTrack> findByCoupleIdAndStatus(UUID coupleId, MediaStatus status);

	List<MediaTrack> findByCoupleId(UUID coupleId);
}
