package com.Semicolon.service.utils;

import com.Semicolon.data.models.OutboxEmail;
import com.Semicolon.data.models.OutboxStatus;
import com.Semicolon.data.models.User;
import com.Semicolon.data.models.UserStatus;
import com.Semicolon.data.repositories.OutboxEmailRepository;
import com.Semicolon.data.repositories.UserRepository;
import com.Semicolon.data.repositories.VerificationTokenRepository;
import com.Semicolon.exceptions.api.OutboxNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Component
public class OutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);

    private final OutboxEmailRepository outboxRepo;
    private final VerificationTokenRepository tokenRepo;
    private final UserRepository userRepo;
    private final ApplicationContext ctx;
    private final Random random = new Random();

    @Value("${SIMULATE_EMAIL_BEHAVIOUR:RANDOM}")
    private String simulateBehaviour;

    public OutboxWorker(OutboxEmailRepository outboxRepo,
                        VerificationTokenRepository tokenRepo,
                        UserRepository userRepo,
                        ApplicationContext ctx) {
        this.outboxRepo = outboxRepo;
        this.tokenRepo = tokenRepo;
        this.userRepo = userRepo;
        this.ctx = ctx;
    }

    /**
     * Perform a single attempt for the outbox row. Runs inside a transaction so
     * lazy associations (e.g. vt.getUser()) can be initialized safely.
     */
    @Transactional
    public OutboxStatus processOutboxAttempt(UUID outboxId) {
        OutboxEmail outbox = outboxRepo.findById(outboxId)
                .orElseThrow(OutboxNotFoundException::new);

        // If already final, return early
        if (outbox.getStatus() == OutboxStatus.SENT || outbox.getStatus() == OutboxStatus.FAILED) {
            return outbox.getStatus();
        }

        Instant now = Instant.now();

        int attempts = outbox.getAttempts() + 1;
        outbox.setAttempts(attempts);
        outbox.setLastAttemptAt(now);

        boolean success = simulateSend(outbox);

        if (success) {
            outbox.setStatus(OutboxStatus.SENT);
            outbox.setNextAttemptAt(null);
            outboxRepo.save(outbox);

            // if verification token present, mark token used and verify user (inside same tx)
            String token = outbox.getVerificationToken();
            if (token != null) {
                tokenRepo.findByToken(token).ifPresent(vt -> {
                    if (!vt.isUsed() && Instant.now().isBefore(vt.getExpiresAt())) {
                        vt.setUsed(true);
                        tokenRepo.save(vt);
                        User user = vt.getUser();
                        user.setStatus(UserStatus.VERIFIED);
                        userRepo.save(user);
                    }
                });
            }

            log.info("[OutboxWorker] Email Successfully sent to {} (token={}) attempt={}",
                    outbox.getToEmail(), outbox.getVerificationToken(), attempts);
            return OutboxStatus.SENT;
        }

        // failed attempt
        if (attempts >= (outbox.getMaxAttempts() == 0 ? 3 : outbox.getMaxAttempts())) {
            outbox.setStatus(OutboxStatus.FAILED);
            outbox.setNextAttemptAt(null);
            outboxRepo.save(outbox);
            log.info("[OutboxWorker] Email failed for {} (token={}) after attempts={}",
                    outbox.getToEmail(), outbox.getVerificationToken(), attempts);
            return OutboxStatus.FAILED;
        } else {
            outbox.setStatus(OutboxStatus.QUEUED);
            long retryDelaySeconds = 2L;
            outbox.setNextAttemptAt(Instant.now().plusSeconds(retryDelaySeconds));
            outboxRepo.save(outbox);
            log.info("[OutboxWorker] Email attempt failed for {} (token={}) attempt={} - requeued for {}",
                    outbox.getToEmail(), outbox.getVerificationToken(), attempts, outbox.getNextAttemptAt());
            return OutboxStatus.QUEUED;
        }
    }

    /**
     * Async trigger: orchestrates retries for the first OutboxEmail found for the verification token.
     * Calls processOutboxAttempt(...) via the Spring proxy so that @Transactional on that method is applied.
     */
    @Async
    public void triggerRetriesForToken(String verificationToken) {
        try {
            Optional<OutboxEmail> maybe = outboxRepo.findByVerificationToken(verificationToken).stream().findFirst();
            if (maybe.isEmpty()) {
                log.warn("[OutboxWorker] No outbox row found for token={}", verificationToken);
                return;
            }
            OutboxEmail e = maybe.get();

            while (true) {
                // fetch fresh state (so we see updates from processOutboxAttempt)
                OutboxEmail fresh = outboxRepo.findById(e.getId()).orElse(null);
                if (fresh == null) {
                    log.warn("[OutboxWorker] outbox row disappeared (id={}), stopping retries", e.getId());
                    return;
                }
                if (fresh.getStatus() == OutboxStatus.SENT || fresh.getStatus() == OutboxStatus.FAILED) {
                    log.info("[OutboxWorker] outbox id={} status is final ({}), stopping retries", fresh.getId(), fresh.getStatus());
                    return;
                }

                Instant now = Instant.now();
                if (fresh.getNextAttemptAt() != null && fresh.getNextAttemptAt().isAfter(now)) {
                    long waitMillis = Math.max(0, fresh.getNextAttemptAt().toEpochMilli() - now.toEpochMilli());
                    try {
                        Thread.sleep(waitMillis);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("[OutboxWorker] triggerRetriesForToken interrupted, exiting");
                        return;
                    }
                }

                // Important: call processOutboxAttempt via Spring proxy so @Transactional is applied
                try {
                    OutboxWorker proxy = ctx.getBean(OutboxWorker.class);
                    proxy.processOutboxAttempt(fresh.getId());
                } catch (Exception ex) {
                    // Log and continue (or decide to break if you prefer)
                    log.error("[OutboxWorker] Exception during processOutboxAttempt for outboxId={}", fresh.getId(), ex);
                }

                // re-check state after attempt
                OutboxEmail after = outboxRepo.findById(fresh.getId()).orElse(null);
                if (after == null) {
                    return;
                }
                if (after.getStatus() == OutboxStatus.QUEUED) {
                    // continue loop and wait for nextAttemptAt if set
                    continue;
                } else {
                    // SENT or FAILED -> exit
                    return;
                }
            }
        } catch (Throwable t) {
            log.error("[OutboxWorker] Uncaught exception in triggerRetriesForToken for token={}", verificationToken, t);
        }
    }

    /**
     * Decide whether to "send" (true) or "fail" (false) for a given OutboxEmail row.
     * Respects per-row forceFail flag and the environment toggle.
     */
    private boolean simulateSend(OutboxEmail e) {
        if (e.isForceFail()) return false;

        String mode = (simulateBehaviour == null) ? "RANDOM" : simulateBehaviour.trim().toUpperCase();
        switch (mode) {
            case "ALWAYS_SUCCESS":
                return true;
            case "ALWAYS_FAIL":
                return false;
            case "RANDOM":
            default:
                return random.nextDouble() < 0.5;
        }
    }
}
