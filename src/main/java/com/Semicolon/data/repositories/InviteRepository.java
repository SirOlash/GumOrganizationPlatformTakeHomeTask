package com.Semicolon.data.repositories;

import com.Semicolon.data.models.Invite;
import com.Semicolon.data.models.InviteStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InviteRepository extends JpaRepository<Invite, UUID> {
    Optional<Invite> findByToken(String token);

    List<Invite> findByEmailIgnoreCaseAndOrganization_IdAndStatusIn(String email, UUID orgId, List<InviteStatus> statuses);

    boolean existsByEmailIgnoreCaseAndOrganization_IdAndStatusIn(String email, UUID orgId, List<InviteStatus> statuses);

    List<Invite> findByEmailIgnoreCaseAndStatusIn(String email, List<InviteStatus> statuses);

    List<Invite> findByStatusInAndExpiresAtBefore(List<InviteStatus> statuses, Instant before);
}
