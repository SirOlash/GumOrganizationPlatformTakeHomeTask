package com.Semicolon.controllers;

import com.Semicolon.dtos.request.*;
import com.Semicolon.dtos.response.AdminRegistrationResponse;
import com.Semicolon.dtos.response.LoginResponse;
import com.Semicolon.dtos.response.VerifyEmailResponse;
import com.Semicolon.service.auth.*;
import com.Semicolon.service.utils.OutboxWorker;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/auth")
public class AuthControllers {
    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);
    @Autowired
    private IRegistrationService registrationService;

    @Autowired
    private IVerifyEmail verifyEmail;

    @Autowired
    private ILoginService loginService;

    @Autowired
    private ILogoutService logoutService;

    @PostMapping("/signup")
    public ResponseEntity<AdminRegistrationResponse> signup(@Valid @RequestBody AdminRegistrationRequest req) {
        AdminRegistrationResponse resp = registrationService.register(req);

        if (resp.getMessage().equals("VERIFICATION_RESENT")) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(resp);
        } else {
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);
        }
    }

    @PostMapping("/verify-email")
    public ResponseEntity<VerifyEmailResponse> verify(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody VerifyEmailRequest body) {

        if (authorization == null || !authorization.toLowerCase().startsWith("bearer ")) {
            return ResponseEntity.badRequest()
                    .body(new VerifyEmailResponse("Missing Authorization header with Bearer token"));
        }

        String token = authorization.substring(7).trim();
        VerifyEmailResponse response = verifyEmail.verifyTokenForEmail(token, body.getEmail());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest body) {
        LoginResponse resp = loginService.login(body);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<LoginResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        LoginResponse response = loginService.refresh(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        log.info("Logout controller called with sessionId={}", request.getSessionId());
        logoutService.logout(request.getSessionId());
        return ResponseEntity.noContent().build();
    }
}
