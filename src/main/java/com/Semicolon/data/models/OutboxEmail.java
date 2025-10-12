package com.Semicolon.data.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Entity
@Table(name = "outbox_email")
public class OutboxEmail {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String toEmail;

    private String subject;

    @Column(length = 5000)
    private String body;

    @Column(name = "verification_token")
    private String verificationToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status = OutboxStatus.QUEUED;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(nullable = false)
    private int maxAttempts = 3;

    private boolean forceFail = false;

    private Instant nextAttemptAt;
    private Instant lastAttemptAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
