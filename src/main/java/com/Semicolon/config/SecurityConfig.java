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

        // create explicit AntPathRequestMatchers
        AntPathRequestMatcher signup = new AntPathRequestMatcher("/v1/auth/signup", "POST");
        AntPathRequestMatcher verify = new AntPathRequestMatcher("/v1/auth/verify-email", "POST");
        AntPathRequestMatcher login = new AntPathRequestMatcher("/v1/auth/login", "POST");
        AntPathRequestMatcher refresh = new AntPathRequestMatcher("/v1/auth/refresh-token", "POST");
        AntPathRequestMatcher logout = new AntPathRequestMatcher("/v1/auth/logout", "POST");
        AntPathRequestMatcher health = new AntPathRequestMatcher("/.well-known/health", null);

        AntPathRequestMatcher h2Console = new AntPathRequestMatcher("/h2-console/**", null);
        AntPathRequestMatcher apiDocs = new AntPathRequestMatcher("/v3/api-docs/**", null);
        AntPathRequestMatcher swaggerUi = new AntPathRequestMatcher("/swagger-ui/**", null);
        AntPathRequestMatcher swaggerHtml = new AntPathRequestMatcher("/swagger-ui.html", null);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(signup, verify, login, refresh, logout,
                                health, h2Console, apiDocs, swaggerUi, swaggerHtml)
                        .permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable);
//                .logout(logoutCfg -> logoutCfg
//                        .logoutUrl("/v1/auth/logout")
//                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(HttpStatus.NO_CONTENT.value()))
//                        .permitAll()
//                );

        http.addFilterBefore(new SessionAuthenticationFilter(sessionRepo),
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
