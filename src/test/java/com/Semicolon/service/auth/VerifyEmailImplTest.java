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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VerifyEmailImplTest {

    @Mock
    private VerificationTokenRepository tokenRepo;

    @Mock
    private UserRepository userRepo;

    @Mock
    private OutboxEmailRepository outboxRepo;

    @Mock
    private OutboxWorker outboxWorker;

    @InjectMocks
    private VerifyEmailImpl verifyEmail;

    private final String TOKEN = "abc-123-token";
    private final String EMAIL = "alice@example.com";


    // Helper to create a verification token with user
    private VerificationToken makeToken(String tokenValue, String email, boolean used, Instant expiresAt) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash("pw");
        u.setStatus(UserStatus.PENDING);
        VerificationToken vt = new VerificationToken(tokenValue, u, expiresAt);
        vt.setUsed(used);
        return vt;
    }

    @Test
    void verify_success_whenWorkerReturnsSent() {
        // arrange
        VerificationToken vt = makeToken(TOKEN, EMAIL, false, Instant.now().plusSeconds(3600));
        when(tokenRepo.findByToken(TOKEN)).thenReturn(Optional.of(vt));

        // simulate outboxRepo.save assigns an id
        when(outboxRepo.save(any(OutboxEmail.class))).thenAnswer(invocation -> {
            OutboxEmail o = invocation.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        // when worker is invoked, it returns SENT
        when(outboxWorker.processOutboxAttempt(any(UUID.class))).thenReturn(OutboxStatus.SENT);

        // act
        VerifyEmailResponse resp = verifyEmail.verifyTokenForEmail(TOKEN, EMAIL);

        // assert
        assertEquals("successful", resp.getMessage());
        verify(outboxRepo, times(1)).save(any(OutboxEmail.class));
        verify(outboxWorker, times(1)).processOutboxAttempt(any(UUID.class));
    }

    @Test
    void verify_queued_whenWorkerReturnsQueued() {
        // arrange
        VerificationToken vt = makeToken(TOKEN, EMAIL, false, Instant.now().plusSeconds(3600));
        when(tokenRepo.findByToken(TOKEN)).thenReturn(Optional.of(vt));

        when(outboxRepo.save(any(OutboxEmail.class))).thenAnswer(invocation -> {
            OutboxEmail o = invocation.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        when(outboxWorker.processOutboxAttempt(any(UUID.class))).thenReturn(OutboxStatus.QUEUED);

        // act
        VerifyEmailResponse resp = verifyEmail.verifyTokenForEmail(TOKEN, EMAIL);

        // assert
        assertEquals("queued", resp.getMessage());
        verify(outboxRepo, times(1)).save(any(OutboxEmail.class));
        verify(outboxWorker, times(1)).processOutboxAttempt(any(UUID.class));
    }

    @Test
    void verify_failed_whenWorkerReturnsFailed() {
        // arrange
        VerificationToken vt = makeToken(TOKEN, EMAIL, false, Instant.now().plusSeconds(3600));
        when(tokenRepo.findByToken(TOKEN)).thenReturn(Optional.of(vt));

        when(outboxRepo.save(any(OutboxEmail.class))).thenAnswer(invocation -> {
            OutboxEmail o = invocation.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        when(outboxWorker.processOutboxAttempt(any(UUID.class))).thenReturn(OutboxStatus.FAILED);

        // act
        VerifyEmailResponse resp = verifyEmail.verifyTokenForEmail(TOKEN, EMAIL);

        // assert
        assertEquals("failed", resp.getMessage());
        verify(outboxRepo, times(1)).save(any(OutboxEmail.class));
        verify(outboxWorker, times(1)).processOutboxAttempt(any(UUID.class));
    }

    @Test
    void verify_throwsTokenInvalid_whenTokenNotFound() {
        // arrange
        when(tokenRepo.findByToken(TOKEN)).thenReturn(Optional.empty());

        // act / assert
        assertThrows(TokenInvalidException.class, () -> verifyEmail.verifyTokenForEmail(TOKEN, EMAIL));
        verify(outboxRepo, never()).save(any());
        verify(outboxWorker, never()).processOutboxAttempt(any());
    }

    @Test
    void verify_throwsTokenExpired_whenExpired() {
        // arrange
        VerificationToken vt = makeToken(TOKEN, EMAIL, false, Instant.now().minusSeconds(10));
        when(tokenRepo.findByToken(TOKEN)).thenReturn(Optional.of(vt));

        // act / assert
        assertThrows(TokenExpiredException.class, () -> verifyEmail.verifyTokenForEmail(TOKEN, EMAIL));
        verify(outboxRepo, never()).save(any());
        verify(outboxWorker, never()).processOutboxAttempt(any());
    }

    @Test
    void verify_throwsTokenUsed_whenUsedAlready() {
        // arrange
        VerificationToken vt = makeToken(TOKEN, EMAIL, true, Instant.now().plusSeconds(3600));
        when(tokenRepo.findByToken(TOKEN)).thenReturn(Optional.of(vt));

        // act / assert
        assertThrows(TokenUsedException.class, () -> verifyEmail.verifyTokenForEmail(TOKEN, EMAIL));
        verify(outboxRepo, never()).save(any());
        verify(outboxWorker, never()).processOutboxAttempt(any());
    }

    @Test
    void verify_throwsTokenInvalid_whenEmailMismatch() {
        // arrange: token exists but attached user has different email
        VerificationToken vt = makeToken(TOKEN, "other@example.com", false, Instant.now().plusSeconds(3600));
        when(tokenRepo.findByToken(TOKEN)).thenReturn(Optional.of(vt));

        // act / assert
        assertThrows(TokenInvalidException.class, () -> verifyEmail.verifyTokenForEmail(TOKEN, EMAIL));
        verify(outboxRepo, never()).save(any());
        verify(outboxWorker, never()).processOutboxAttempt(any());
    }
}
