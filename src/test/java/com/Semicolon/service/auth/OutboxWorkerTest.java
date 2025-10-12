package com.Semicolon.service.auth;

import com.Semicolon.data.models.*;
import com.Semicolon.data.repositories.OutboxEmailRepository;
import com.Semicolon.data.repositories.UserRepository;
import com.Semicolon.data.repositories.VerificationTokenRepository;
import com.Semicolon.service.utils.OutboxWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxWorkerTest {

    @Mock
    private OutboxEmailRepository outboxRepo;

    @Mock
    private VerificationTokenRepository tokenRepo;

    @Mock
    private UserRepository userRepo;

    @InjectMocks
    private OutboxWorker outboxWorker;

    private final UUID outboxId = UUID.randomUUID();
    private final String tokenStr = "tok-1";

    @BeforeEach
    void setUp() {
        // Ensure deterministic behavior by setting the env-mode via ReflectionTestUtils.
        // You can set to "ALWAYS_SUCCESS", "ALWAYS_FAIL" or "RANDOM".
        ReflectionTestUtils.setField(outboxWorker, "simulateBehaviour", "ALWAYS_SUCCESS");
    }

    // Helper to build an OutboxEmail with defaults
    private OutboxEmail makeOutbox(int attempts, int maxAttempts, boolean forceFail, String verificationToken) {
        OutboxEmail o = new OutboxEmail();
        o.setId(outboxId);
        o.setToEmail("alice@example.com");
        o.setVerificationToken(verificationToken);
        o.setAttempts(attempts);
        o.setMaxAttempts(maxAttempts);
        o.setForceFail(forceFail);
        o.setStatus(OutboxStatus.QUEUED);
        o.setCreatedAt(Instant.now());
        return o;
    }

    @Test
    void processOutboxAttempt_succeeds_marksSent_and_verifiesTokenAndUser() {
        // arrange
        OutboxEmail e = makeOutbox(0, 3, false, tokenStr);
        when(outboxRepo.findById(outboxId)).thenReturn(Optional.of(e));
        when(outboxRepo.save(any(OutboxEmail.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Prepare a verification token tied to a user
        User u = new User();
        u.setEmail("alice@example.com");
        u.setPasswordHash("pw");
        u.setStatus(UserStatus.PENDING);

        VerificationToken vt = new VerificationToken(tokenStr, u, Instant.now().plusSeconds(3600));
        vt.setUsed(false);
        when(tokenRepo.findByToken(tokenStr)).thenReturn(Optional.of(vt));
        when(tokenRepo.save(any(VerificationToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // ensure simulateBehaviour = ALWAYS_SUCCESS (set in setUp)

        // act
        OutboxStatus result = outboxWorker.processOutboxAttempt(outboxId);

        // assert
        assertThat(result).isEqualTo(OutboxStatus.SENT);

        // verify outbox saved with SENT status
        ArgumentCaptor<OutboxEmail> outCaptor = ArgumentCaptor.forClass(OutboxEmail.class);
        verify(outboxRepo, atLeastOnce()).save(outCaptor.capture());
        OutboxEmail saved = outCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.SENT);

        // verify token and user were updated
        ArgumentCaptor<VerificationToken> tokenCaptor = ArgumentCaptor.forClass(VerificationToken.class);
        verify(tokenRepo).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().isUsed()).isTrue();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getStatus()).isEqualTo(UserStatus.VERIFIED);
    }

    @Test
    void processOutboxAttempt_whenAttemptsExhausted_marksFailed() {
        // arrange: set worker to ALWAYS_FAIL
        ReflectionTestUtils.setField(outboxWorker, "simulateBehaviour", "ALWAYS_FAIL");

        // set attempts to maxAttempts - 1 so this attempt will be the final one
        OutboxEmail e = makeOutbox(2, 3, false, tokenStr); // attempts=2, max=3 -> this will be attempt 3
        when(outboxRepo.findById(outboxId)).thenReturn(Optional.of(e));
        when(outboxRepo.save(any(OutboxEmail.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // token present but will not be marked used
        User u = new User();
        u.setEmail("alice@example.com");
        u.setPasswordHash("pw");
        u.setStatus(UserStatus.PENDING);
        VerificationToken vt = new VerificationToken(tokenStr, u, Instant.now().plusSeconds(3600));
        vt.setUsed(false);
//        when(tokenRepo.findByToken(tokenStr)).thenReturn(Optional.of(vt));

        // act
        OutboxStatus result = outboxWorker.processOutboxAttempt(outboxId);

        // assert
        assertThat(result).isEqualTo(OutboxStatus.FAILED);

        // verify saved and status is FAILED
        ArgumentCaptor<OutboxEmail> outCaptor = ArgumentCaptor.forClass(OutboxEmail.class);
        verify(outboxRepo, atLeastOnce()).save(outCaptor.capture());
        OutboxEmail saved = outCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.FAILED);

        // token and user should NOT be marked used/verified in this path
        verify(tokenRepo, never()).save(any());
        verify(userRepo, never()).save(any());
    }
}
