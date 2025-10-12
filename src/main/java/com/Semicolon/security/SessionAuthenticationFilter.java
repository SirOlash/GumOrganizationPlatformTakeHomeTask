package com.Semicolon.security;

import com.Semicolon.data.repositories.AuthSessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
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
                    sessionRepo.findBySessionId(token).ifPresent(session -> {
                        try {
                            if (!session.isRevoked() && session.getExpiresAt().isAfter(Instant.now())) {
                                UUID userId = session.getUser().getId();
                                var authToken = new UsernamePasswordAuthenticationToken(
                                        userId,
                                        null,
                                        List.of(new SimpleGrantedAuthority("ROLE_TALENT"))
                                );
                                // optional: include session id in details
                                authToken.setDetails(session.getSessionId());
                                SecurityContextHolder.getContext().setAuthentication(authToken);
                            }
                        } catch (Exception ex) {
                            log.warn("Error building authentication from session {}, skipping authentication", token, ex);
                        }
                    });
                }
            }
        } catch (Exception e) {
            // log full exception so we can debug unexpected errors
            log.debug("SessionAuthenticationFilter error", e);
        }

        chain.doFilter(req, res);
    }
}

