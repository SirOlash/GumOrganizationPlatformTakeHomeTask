package com.Semicolon.data.repositories;

import com.Semicolon.data.models.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository <RefreshToken, UUID>{
    Optional<RefreshToken> findByToken(String token);
    List<RefreshToken> findBySession_Id(UUID sessionId);

}
