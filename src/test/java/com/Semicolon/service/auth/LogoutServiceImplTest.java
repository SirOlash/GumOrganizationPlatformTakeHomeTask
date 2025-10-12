package com.Semicolon.service.auth;

import com.Semicolon.data.models.AuthSession;
import com.Semicolon.data.models.RefreshToken;
import com.Semicolon.data.repositories.AuthSessionRepository;
import com.Semicolon.data.repositories.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogoutServiceImplTest {

    @Mock
    private AuthSessionRepository sessionRepo;

    @Mock
    private RefreshTokenRepository refreshRepo;

    @InjectMocks
    private LogoutServiceImpl logoutService;

    private final UUID sessionUuid = UUID.randomUUID();
    private final String sessionId = "sess-123";

    @BeforeEach
    void setUp() {
        // Mockito will inject mocks
    }

    private RefreshToken makeRefreshToken(UUID id, AuthSession session, boolean revoked) {
        RefreshToken rt = new RefreshToken();
        rt.setId(id);
        rt.setSession(session);
        rt.setToken("rt-" + id);
        rt.setRevoked(revoked);
        return rt;
    }

    @Test
    void logout_existingSession_revokesSessionAndTokens() {
        // arrange
        AuthSession s = new AuthSession();
        s.setId(sessionUuid);
        s.setSessionId(sessionId);
        s.setRevoked(false);

        RefreshToken t1 = makeRefreshToken(UUID.randomUUID(), s, false);
        RefreshToken t2 = makeRefreshToken(UUID.randomUUID(), s, false);
        List<RefreshToken> tokens = List.of(t1, t2);

        when(sessionRepo.findBySessionId(sessionId)).thenReturn(Optional.of(s));
        when(refreshRepo.findBySession_Id(sessionUuid)).thenReturn(tokens);
        when(sessionRepo.save(any(AuthSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(refreshRepo.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // act
        logoutService.logout(sessionId);

        // assert - session revoked and saved
        ArgumentCaptor<AuthSession> sessionCaptor = ArgumentCaptor.forClass(AuthSession.class);
        verify(sessionRepo, times(1)).save(sessionCaptor.capture());
        AuthSession savedSession = sessionCaptor.getValue();
        assertThat(savedSession.isRevoked()).isTrue();

        // assert - tokens revoked and saved in batch
        ArgumentCaptor<List<RefreshToken>> listCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(refreshRepo, times(1)).saveAll(listCaptor.capture());
        List<RefreshToken> savedTokens = listCaptor.getValue();
        assertThat(savedTokens).hasSize(2);
        assertThat(savedTokens).allMatch(RefreshToken::isRevoked);
    }

    @Test
    void logout_missingSession_noOp() {
        // arrange
        when(sessionRepo.findBySessionId("nope")).thenReturn(Optional.empty());

        // act
        logoutService.logout("nope");

        // assert - no saves performed
        verify(sessionRepo, never()).save(any());
        verify(refreshRepo, never()).saveAll(anyList());
    }
}
