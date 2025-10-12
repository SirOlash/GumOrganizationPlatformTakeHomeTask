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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginServiceImplTest {

    @Mock
    private UserRepository userRepo;

    @Mock
    private AuthSessionRepository sessionRepo;

    @Mock
    private RefreshTokenRepository refreshRepo;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private LoginServiceImpl loginService;

    private final String EMAIL = "alice@example.com";
    private final String PASSWORD = "secretpw";

    private LoginRequest makeLoginRequest() {
        LoginRequest r = new LoginRequest();
        r.setEmail("alice@example.com");
        r.setPassword("secretpw");
        return r;
    }

    private User makeUser(UserStatus status) {
        User u = new User();
        u.setEmail("alice@example.com");
        u.setPasswordHash("hashed");
        u.setStatus(status);
        return u;
    }

    @Test
    void login_success_createsSessionAndRefreshToken() {
        // arrange
        LoginRequest req = makeLoginRequest();
        User user = makeUser(UserStatus.VERIFIED);

        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, "hashed")).thenReturn(true);

        // make save calls return same object (simulate JPA)
        when(sessionRepo.save(any(AuthSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(refreshRepo.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // act
        LoginResponse resp = loginService.login(req);

        // assert
        assertThat(resp).isNotNull();
        assertThat(resp.getMessage()).containsIgnoringCase("login successful");
        assertThat(resp.getAccessToken()).isNotBlank();
        assertThat(resp.getRefreshToken()).isNotBlank();

        // verify session + refresh saved
        verify(sessionRepo, times(1)).save(any(AuthSession.class));
        verify(refreshRepo, times(1)).save(any(RefreshToken.class));
    }

    @Test
    void login_unknownEmail_throwsInvalidCredentials() {
        // arrange
        LoginRequest req = makeLoginRequest();
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.empty());

        // act / assert
        assertThatThrownBy(() -> loginService.login(req))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(sessionRepo, never()).save(any());
        verify(refreshRepo, never()).save(any());
    }

    @Test
    void login_badPassword_throwsInvalidCredentials() {
        // arrange
        LoginRequest req = makeLoginRequest();
        User user = makeUser(UserStatus.VERIFIED);
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, "hashed")).thenReturn(false);

        // act / assert
        assertThatThrownBy(() -> loginService.login(req))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(sessionRepo, never()).save(any());
        verify(refreshRepo, never()).save(any());
    }

    @Test
    void login_unverifiedEmail_throwsEmailNotVerified() {
        // arrange
        LoginRequest req = makeLoginRequest();
        User user = makeUser(UserStatus.PENDING);
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, "hashed")).thenReturn(true);

        // act / assert
        assertThatThrownBy(() -> loginService.login(req))
                .isInstanceOf(EmailNotVerifiedException.class);

        verify(sessionRepo, never()).save(any());
        verify(refreshRepo, never()).save(any());
    }

    @Test
    void refresh_success_rotatesSessionAndRefreshToken() {
        // arrange
        String oldSessionId = "old-session-id";
        AuthSession session = new AuthSession();
        session.setSessionId(oldSessionId);
        session.setExpiresAt(Instant.now().plus(3, ChronoUnit.DAYS));
        session.setRevoked(false);

        RefreshToken existing = new RefreshToken();
        existing.setToken("existing-refresh");
        existing.setSession(session);
        existing.setExpiresAt(Instant.now().plus(10, ChronoUnit.DAYS));
        existing.setRevoked(false);

        when(refreshRepo.findByToken("existing-refresh")).thenReturn(Optional.of(existing));
        when(refreshRepo.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionRepo.save(any(AuthSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // act
        LoginResponse resp = loginService.refresh("existing-refresh");

        // assert
        assertThat(resp).isNotNull();
        assertThat(resp.getMessage()).containsIgnoringCase("token refreshed");
        assertThat(resp.getAccessToken()).isNotBlank();
        assertThat(resp.getRefreshToken()).isNotBlank();
        assertThat(resp.getAccessToken()).isNotEqualTo(oldSessionId);

        // verify revoke of old refresh
        verify(refreshRepo, atLeastOnce()).save(argThat(rt -> rt.getToken().equals("existing-refresh") && rt.isRevoked()));
        // verify new refresh saved
        verify(refreshRepo, atLeastOnce()).save(argThat(rt -> !rt.getToken().equals("existing-refresh")));
        // verify session updated
        verify(sessionRepo, times(1)).save(any(AuthSession.class));
    }

    @Test
    void refresh_unknownToken_throwsInvalidCredentials() {
        // arrange
        when(refreshRepo.findByToken("bad-token")).thenReturn(Optional.empty());

        // act / assert
        assertThatThrownBy(() -> loginService.refresh("bad-token"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(sessionRepo, never()).save(any());
    }

    @Test
    void refresh_revokedOrExpiredToken_throwsInvalidCredentials() {
        // arrange: revoked token
        AuthSession session = new AuthSession();
        session.setSessionId("s");
        session.setExpiresAt(Instant.now().plus(3, ChronoUnit.DAYS));
        session.setRevoked(false);

        RefreshToken existing = new RefreshToken();
        existing.setToken("revoked-refresh");
        existing.setSession(session);
        existing.setExpiresAt(Instant.now().plus(10, ChronoUnit.DAYS));
        existing.setRevoked(true); // revoked

        when(refreshRepo.findByToken("revoked-refresh")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> loginService.refresh("revoked-refresh"))
                .isInstanceOf(InvalidCredentialsException.class);

        // arrange: expired token
        RefreshToken expired = new RefreshToken();
        expired.setToken("expired-refresh");
        expired.setSession(session);
        expired.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        expired.setRevoked(false);
        when(refreshRepo.findByToken("expired-refresh")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> loginService.refresh("expired-refresh"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void refresh_sessionInvalid_throwsInvalidCredentials() {
        // arrange: session revoked
        AuthSession session = new AuthSession();
        session.setSessionId("s");
        session.setExpiresAt(Instant.now().plus(3, ChronoUnit.DAYS));
        session.setRevoked(true);

        RefreshToken existing = new RefreshToken();
        existing.setToken("tok");
        existing.setSession(session);
        existing.setExpiresAt(Instant.now().plus(10, ChronoUnit.DAYS));
        existing.setRevoked(false);

        when(refreshRepo.findByToken("tok")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> loginService.refresh("tok"))
                .isInstanceOf(InvalidCredentialsException.class);

        // arrange: session expired
        session.setRevoked(false);
        session.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        when(refreshRepo.findByToken("tok")).thenReturn(Optional.of(existing)); // same object now expired

        assertThatThrownBy(() -> loginService.refresh("tok"))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
