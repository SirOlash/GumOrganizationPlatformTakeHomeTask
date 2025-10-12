package com.Semicolon.service.auth;

import com.Semicolon.dtos.response.VerifyEmailResponse;

public interface IVerifyEmail {
    VerifyEmailResponse verifyTokenForEmail(String token, String email);
}
