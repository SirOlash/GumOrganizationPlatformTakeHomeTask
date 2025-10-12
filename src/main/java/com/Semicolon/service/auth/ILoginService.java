package com.Semicolon.service.auth;

import com.Semicolon.dtos.request.LoginRequest;
import com.Semicolon.dtos.response.LoginResponse;

public interface ILoginService {
    LoginResponse login(LoginRequest req);
    LoginResponse refresh(String providedRefreshToken);
}
