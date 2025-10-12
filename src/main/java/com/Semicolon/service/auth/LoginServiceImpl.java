package com.Semicolon.service.auth;

import com.Semicolon.data.models.AuthSession;
import com.Semicolon.data.models.RefreshToken;
import com.Semicolon.data.models.User;
import com.Semicolon.data.models.UserStatus;
import com.Semicolon.data.repositories.AuthSessionRepository;
import com.Semicolon.data.repositories.RefreshTokenRepository;
import com.Semicolon.data.repositories.UserRepository;
import com.Semicolon.dtos.request.LoginRequest;
import com.Semicolon.dtos.response.LoginResponse;
import com.Semicolon.exceptions.api.EmailNotVerifiedException;
import com.Semicolon.exceptions.api.InvalidCredentialsException;
import com.Semicolon.exceptions.api.RateLimitedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginServiceImpl implements ILoginService {
    private final UserRepository userRepo;
    private final AuthSessionRepository sessionRepo;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshRepo;
    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    private static final int MAX_ATTEMPTS = 5;
    private static final int WINDOW_MINUTES = 15;
    private static final int LOCK_MINUTES = 15;
    private static final long SESSION_DAYS = 7L;
    private static final long REFRESH_DAYS = 30L;

    public LoginServiceImpl(UserRepository userRepo,
                            AuthSessionRepository sessionRepo,
                            RefreshTokenRepository refreshRepo,
                            PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.sessionRepo = sessionRepo;
        this.refreshRepo = refreshRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest req) {
        String emailKey = req.getEmail().trim().toLowerCase();

        if (isBlocked(emailKey)) {
            throw new RateLimitedException();
        }

        Optional<User> optUser = userRepo.findByEmail(emailKey);
        if (optUser.isEmpty()) {
            recordFailedAttempt(emailKey);
            throw new InvalidCredentialsException();
        }
        User user = optUser.get();

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            recordFailedAttempt(emailKey);
            throw new InvalidCredentialsException();
        }

        if (user.getStatus() != UserStatus.VERIFIED) {
            // Note: do not clear attempts here; keep existing behaviour
            throw new EmailNotVerifiedException();
        }

        // Successful login — clear attempt count
        clearAttempts(emailKey);

        // Create session
        String sessionId = UUID.randomUUID().toString();
        Instant sessionExpires = Instant.now().plus(SESSION_DAYS, ChronoUnit.DAYS);

        AuthSession s = new AuthSession();
        s.setSessionId(sessionId);
        s.setUser(user);
        s.setExpiresAt(sessionExpires);
        s.setRevoked(false);

        sessionRepo.save(s);

        // Create refresh token bound to the session
        String refreshToken = UUID.randomUUID().toString();
        Instant refreshExpires = Instant.now().plus(REFRESH_DAYS, ChronoUnit.DAYS);

        RefreshToken rt = new RefreshToken();
        rt.setToken(refreshToken);
        rt.setSession(s);
        rt.setExpiresAt(refreshExpires);
        rt.setRevoked(false);
        refreshRepo.save(rt);

        return new LoginResponse("Login successful", sessionId, refreshToken);
    }

    @Override
    @Transactional
    public LoginResponse refresh(String providedRefreshToken) {
        RefreshToken existing = refreshRepo.findByToken(providedRefreshToken)
                .orElseThrow(InvalidCredentialsException::new);

        // token-level checks
        if (existing.isRevoked() || existing.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidCredentialsException();
        }

        // session-level checks: ensure session is still valid
        AuthSession session = existing.getSession();
        if (session == null || session.isRevoked() || session.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidCredentialsException();
        }

        // revoke the used refresh token
        existing.setRevoked(true);
        refreshRepo.save(existing);

        // rotate session id and extend expiry
        String newSessionId = UUID.randomUUID().toString();
        session.setSessionId(newSessionId);
        session.setExpiresAt(Instant.now().plus(SESSION_DAYS, ChronoUnit.DAYS));
        sessionRepo.save(session);

        // issue new refresh token
        String newRefreshToken = UUID.randomUUID().toString();
        RefreshToken rt = new RefreshToken();
        rt.setToken(newRefreshToken);
        rt.setSession(session);
        rt.setExpiresAt(Instant.now().plus(REFRESH_DAYS, ChronoUnit.DAYS));
        rt.setRevoked(false);
        refreshRepo.save(rt);

        return new LoginResponse("Token refreshed", newSessionId, newRefreshToken);
    }

    /* -------------------- attempt helper methods -------------------- */

    private boolean isBlocked(String email) {
        Attempt a = attempts.get(email);
        if (a == null) return false;

        Instant now = Instant.now();
        // If currently locked
        if (a.lockedUntil != null && now.isBefore(a.lockedUntil)) return true;

        // If window has expired, remove and allow fresh attempts
        if (now.isAfter(a.windowStart.plus(WINDOW_MINUTES, ChronoUnit.MINUTES))) {
            attempts.remove(email);
            return false;
        }

        // not locked
        return false;
    }

    private void recordFailedAttempt(String email) {
        Instant now = Instant.now();
        attempts.compute(email, (k, a) -> {
            if (a == null) return new Attempt(1, now, null);
            // if window expired -> reset
            if (now.isAfter(a.windowStart.plus(WINDOW_MINUTES, ChronoUnit.MINUTES))) {
                return new Attempt(1, now, null);
            }
            int newCount = a.count + 1;
            Instant lockedUntil = a.lockedUntil;
            if (newCount >= MAX_ATTEMPTS) {
                lockedUntil = now.plus(LOCK_MINUTES, ChronoUnit.MINUTES);
            }
            return new Attempt(newCount, a.windowStart, lockedUntil);
        });
    }

    private void clearAttempts(String email) {
        attempts.remove(email);
    }

    private static class Attempt {
        final int count;
        final Instant windowStart;
        final Instant lockedUntil;
        Attempt(int count, Instant windowStart, Instant lockedUntil) {
            this.count = count;
            this.windowStart = windowStart;
            this.lockedUntil = lockedUntil;
        }
    }
}
