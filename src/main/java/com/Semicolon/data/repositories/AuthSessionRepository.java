package com.Semicolon.data.repositories;

import com.Semicolon.data.models.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {
    Optional<AuthSession> findBySessionId(String sessionId);

}
