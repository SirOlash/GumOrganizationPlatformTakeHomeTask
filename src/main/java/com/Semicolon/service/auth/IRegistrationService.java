package com.Semicolon.service.auth;

import com.Semicolon.dtos.request.AdminRegistrationRequest;
import com.Semicolon.dtos.response.AdminRegistrationResponse;

public interface IRegistrationService {
    AdminRegistrationResponse register(AdminRegistrationRequest request);
}
