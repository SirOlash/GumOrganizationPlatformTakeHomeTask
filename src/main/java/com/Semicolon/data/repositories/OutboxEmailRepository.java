package com.Semicolon.data.repositories;

import com.Semicolon.data.models.OutboxEmail;
import com.Semicolon.data.models.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEmailRepository extends JpaRepository <OutboxEmail, UUID>{
    List<OutboxEmail> findByStatus(OutboxStatus status);
    List<OutboxEmail> findByVerificationToken(String token);
}
