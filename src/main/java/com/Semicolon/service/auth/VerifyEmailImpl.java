package com.Semicolon.service.auth;

import com.Semicolon.data.models.*;
import com.Semicolon.data.repositories.OutboxEmailRepository;
import com.Semicolon.data.repositories.UserRepository;
import com.Semicolon.data.repositories.VerificationTokenRepository;
import com.Semicolon.dtos.response.VerifyEmailResponse;
import com.Semicolon.exceptions.api.TokenExpiredException;
import com.Semicolon.exceptions.api.TokenInvalidException;
import com.Semicolon.exceptions.api.TokenUsedException;
import com.Semicolon.service.utils.OutboxWorker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;

@Service
public class VerifyEmailImpl implements IVerifyEmail{
    private final VerificationTokenRepository tokenRepo;
    private final UserRepository userRepo;
    private final OutboxEmailRepository outboxRepo;
    private final OutboxWorker outboxWorker;

    public VerifyEmailImpl(VerificationTokenRepository tokenRepo,
                           UserRepository userRepo,
                           OutboxEmailRepository outboxRepo,
                           OutboxWorker outboxWorker) {
        this.tokenRepo = tokenRepo;
        this.userRepo = userRepo;
        this.outboxRepo = outboxRepo;
        this.outboxWorker = outboxWorker;
    }

    @Transactional
    public VerifyEmailResponse verifyTokenForEmail(String token, String email) {
        VerificationToken verifiedToken = tokenRepo.findByToken(token)
                .orElseThrow(TokenInvalidException::new);

        if (Instant.now().isAfter(verifiedToken.getExpiresAt())) {
            throw new TokenExpiredException();
        }

        if (verifiedToken.isUsed()) {
            throw new TokenUsedException();
        }

        User user = verifiedToken.getUser();
        if (user == null || !user.getEmail().equalsIgnoreCase(email)) {
            throw new TokenInvalidException();
        }

        OutboxEmail outbox = new OutboxEmail();
        outbox.setVerificationToken(token);
        outbox.setToEmail(user.getEmail());
        outbox.setSubject("Verify your account");
        outbox.setBody("Verification for token: " + token);
        outbox.setAttempts(0);
        outbox.setMaxAttempts(3);
        outbox.setStatus(OutboxStatus.QUEUED);
        outbox.setForceFail(false);
        outbox.setNextAttemptAt(null);
        outbox.setLastAttemptAt(null);
        outbox.setCreatedAt(Instant.now());
        outboxRepo.save(outbox);

        OutboxStatus resultStatus = outboxWorker.processOutboxAttempt(outbox.getId());

        if (resultStatus == OutboxStatus.SENT) {
            return new VerifyEmailResponse("Verification successful. You can now log in.");
        } else if (resultStatus == OutboxStatus.QUEUED) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    outboxWorker.triggerRetriesForToken(outbox.getVerificationToken());
                }
            });
            return new VerifyEmailResponse("Verification queued");
        } else {
            return new VerifyEmailResponse("Verification failed");
        }

//        verifiedToken.setUsed(true);
//        user.setStatus(UserStatus.VERIFIED);
//
//        userRepo.save(user);
//        tokenRepo.save(verifiedToken);

//        return new VerifyEmailResponse("You can now log in");
    }
}
