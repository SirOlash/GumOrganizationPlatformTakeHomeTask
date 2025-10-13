package com.Semicolon.config;

import com.Semicolon.data.repositories.AuthSessionRepository;
import com.Semicolon.security.SessionAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.AuthenticationEntryPoint;

@Configuration
public class SecurityConfig {

    private final AuthSessionRepository sessionRepo;

    public SecurityConfig(AuthSessionRepository sessionRepo) {
        this.sessionRepo = sessionRepo;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        // explicit AntPathRequestMatchers (method=null => any HTTP method)
        AntPathRequestMatcher signup     = new AntPathRequestMatcher("/v1/auth/signup", "POST");
        AntPathRequestMatcher verify     = new AntPathRequestMatcher("/v1/auth/verify-email", "POST");
        AntPathRequestMatcher login      = new AntPathRequestMatcher("/v1/auth/login", "POST");
        AntPathRequestMatcher refresh    = new AntPathRequestMatcher("/v1/auth/refresh-token", "POST");
        AntPathRequestMatcher logout     = new AntPathRequestMatcher("/v1/auth/logout", "POST");

        // health & metrics (permit these to be called without auth)
        AntPathRequestMatcher health     = new AntPathRequestMatcher("/.well-known/health", null);
        AntPathRequestMatcher metrics    = new AntPathRequestMatcher("/metrics", null);

        // dev/docs endpoints (allow any method)
        AntPathRequestMatcher h2Console  = new AntPathRequestMatcher("/h2-console/**", null);
        AntPathRequestMatcher apiDocs    = new AntPathRequestMatcher("/v3/api-docs/**", null);
        AntPathRequestMatcher swaggerUi  = new AntPathRequestMatcher("/swagger-ui/**", null);
        AntPathRequestMatcher swaggerHtml= new AntPathRequestMatcher("/swagger-ui.html", null);

        // define a simple entry point to return 401 if not authenticated (clearer than default)
        AuthenticationEntryPoint entryPoint = (request, response, authException) ->
                response.sendError(HttpStatus.UNAUTHORIZED.value(), "Unauthorized");

        http
                .csrf(AbstractHttpConfigurer::disable) // JSON API, token-based auth
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin())) // allow H2 console frames
                .exceptionHandling(eh -> eh.authenticationEntryPoint(entryPoint))
                .authorizeHttpRequests(auth -> auth
                        // allow unauthenticated access to these specific endpoints
                        .requestMatchers(signup, verify, login, refresh, logout,
                                health, metrics, h2Console, apiDocs, swaggerUi, swaggerHtml)
                        .permitAll()
                        // all other requests require authentication
                        .anyRequest().authenticated()
                )
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable);

        // custom filter that sets SecurityContext from sessionId header
        http.addFilterBefore(new SessionAuthenticationFilter(sessionRepo),
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
