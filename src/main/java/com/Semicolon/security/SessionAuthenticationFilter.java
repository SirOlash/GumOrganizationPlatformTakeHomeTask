package com.Semicolon.security;

import com.Semicolon.data.models.AuthSession;
import com.Semicolon.data.repositories.AuthSessionRepository;
import jakarta.persistence.PersistenceException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.hibernate5.HibernateJdbcException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SessionAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(SessionAuthenticationFilter.class);
    private final AuthSessionRepository sessionRepo;

    public SessionAuthenticationFilter(AuthSessionRepository sessionRepo) {
        this.sessionRepo = sessionRepo;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest req,
                                    @NonNull HttpServletResponse res,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        try {
            String auth = req.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                String token = auth.substring(7).trim();
                if (!token.isEmpty()) {
                    // try to fetch session with user (see repository helper findBySessionIdFetchUser below)
                    Optional<AuthSession> maybeSession = Optional.empty();
                    try {
                        maybeSession = sessionRepo.findBySessionId(token); // prefer join-fetch
                    } catch (NoSuchMethodError | AbstractMethodError e) {
                        // repository doesn't have the fetch method (older code) => fallback to normal find
                        maybeSession = sessionRepo.findBySessionId(token);
                    } catch (Exception ex) {
                        // any other exception when trying fetch-with-join (log & fallback to simple find)
                        log.debug("fetch-with-join attempt failed, falling back to simple findBySessionId: {}", ex.toString());
                        try { maybeSession = sessionRepo.findBySessionId(token); } catch (Exception ignore) {}
                    }

                    maybeSession.ifPresent(session -> {
                        try {
                            if (!session.isRevoked() && session.getExpiresAt().isAfter(Instant.now())) {
                                // try to read role from session.getUser() - join fetch prevents lazy issues
                                String roleName;
                                try {
                                    roleName = session.getUser() != null ? session.getUser().getRole().name() : null;
                                } catch (Exception roleEx) {
                                    // defensive: if anything fails when accessing user, log and skip auth
                                    log.debug("Could not access user role for session {}: {}", session.getSessionId(), roleEx.toString());
                                    return;
                                }

                                if (roleName == null) {
                                    log.debug("No role found for user on session {}", session.getSessionId());
                                    return;
                                }

                                UUID userId = session.getUser().getId();
                                var authToken = new UsernamePasswordAuthenticationToken(
                                        userId,
                                        null,
                                        List.of(new SimpleGrantedAuthority("ROLE_" + roleName))
                                );
                                authToken.setDetails(session.getSessionId());
                                SecurityContextHolder.getContext().setAuthentication(authToken);
                            }
                        } catch (Exception exInner) {
                            log.debug("Error building authentication from session {}, skipping authentication: {}",
                                    token, exInner.toString());
                        }
                    });
                }
            }
        } catch (Exception e) {
            log.debug("SessionAuthenticationFilter error", e);
        }

        chain.doFilter(req, res);
    }
}
