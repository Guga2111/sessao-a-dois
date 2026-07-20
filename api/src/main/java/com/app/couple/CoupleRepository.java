package com.app.couple;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CoupleRepository extends JpaRepository<Couple, UUID> {

	Optional<Couple> findByInviteCode(String inviteCode);

	Optional<Couple> findByUser1IdOrUser2Id(UUID user1Id, UUID user2Id);

	boolean existsByInviteCode(String inviteCode);
}
