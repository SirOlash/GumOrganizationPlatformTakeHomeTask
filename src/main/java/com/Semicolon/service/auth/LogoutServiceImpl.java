package com.Semicolon.service.auth;

import com.Semicolon.data.models.AuthSession;
import com.Semicolon.data.models.RefreshToken;
import com.Semicolon.data.repositories.AuthSessionRepository;
import com.Semicolon.data.repositories.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class LogoutServiceImpl implements ILogoutService {
    private final AuthSessionRepository sessionRepo;
    private final RefreshTokenRepository refreshRepo;

    public LogoutServiceImpl(AuthSessionRepository sessionRepo, RefreshTokenRepository refreshRepo) {
        this.sessionRepo = sessionRepo;
        this.refreshRepo = refreshRepo;
    }

    /**
     * Logout: revoke session and any refresh tokens tied to the session.
     * No-op if sessionId not found.
     */
    @Override
    @Transactional
    public void logout(String sessionId) {
        Optional<AuthSession> sOpt = sessionRepo.findBySessionId(sessionId);
        if (sOpt.isEmpty()) return;

        AuthSession s = sOpt.get();
        s.setRevoked(true);
        sessionRepo.save(s);

        // Use repository query to find only tokens for this session (avoid scanning all tokens)
        UUID sid = s.getId();
        if (sid == null) return;

        List<RefreshToken> tokens = refreshRepo.findBySession_Id(sid);
        if (tokens == null || tokens.isEmpty()) return;

        tokens.forEach(t -> t.setRevoked(true));
        refreshRepo.saveAll(tokens);
    }
}
