package com.Semicolon.config;

import com.Semicolon.data.repositories.AuthSessionRepository;
import com.Semicolon.security.SessionAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final AuthSessionRepository sessionRepo;

    public SecurityConfig(AuthSessionRepository sessionRepo) {
        this.sessionRepo = sessionRepo;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Completely bypass Spring Security filters for health/metrics endpoints.
     * This is intentional for monitoring endpoints (they won't go through auth filters).
     */
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring()
                // either pass path strings:
                .requestMatchers("/.well-known/health", "/metrics");
        // OR use AntPathRequestMatcher objects if you prefer:
        // .requestMatchers(new AntPathRequestMatcher("/.well-known/health"),
        //                  new AntPathRequestMatcher("/metrics"));
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        // explicit AntPathRequestMatchers (method=null => any HTTP method)
        AntPathRequestMatcher signup     = new AntPathRequestMatcher("/v1/auth/signup", "POST");
        AntPathRequestMatcher verify     = new AntPathRequestMatcher("/v1/auth/verify-email", "POST");
        AntPathRequestMatcher login      = new AntPathRequestMatcher("/v1/auth/login", "POST");
        AntPathRequestMatcher refresh    = new AntPathRequestMatcher("/v1/auth/refresh-token", "POST");
        AntPathRequestMatcher logout     = new AntPathRequestMatcher("/v1/auth/logout", "POST");

        AntPathRequestMatcher h2Console  = new AntPathRequestMatcher("/h2-console/**", null);
        AntPathRequestMatcher apiDocs    = new AntPathRequestMatcher("/v3/api-docs/**", null);
        AntPathRequestMatcher swaggerUi  = new AntPathRequestMatcher("/swagger-ui/**", null);
        AntPathRequestMatcher swaggerHtml= new AntPathRequestMatcher("/swagger-ui.html", null);

        AuthenticationEntryPoint entryPoint = (request, response, authException) ->
                response.sendError(HttpStatus.UNAUTHORIZED.value(), "Unauthorized");

        http
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .exceptionHandling(eh -> eh.authenticationEntryPoint(entryPoint))
                .authorizeHttpRequests(auth -> auth
                        // endpoints explicitly allowed (health & metrics are ignored above)
                        .requestMatchers(signup, verify, login, refresh, logout,
                                h2Console, apiDocs, swaggerUi, swaggerHtml)
                        .permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable);

        http.addFilterBefore(new SessionAuthenticationFilter(sessionRepo),
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
