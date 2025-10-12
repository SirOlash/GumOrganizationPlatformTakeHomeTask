package com.Semicolon.service.profile;

import com.Semicolon.dtos.request.UpdateOrgProfileRequest;
import com.Semicolon.dtos.response.OrganizationProfileResponse;

import java.util.UUID;

public interface IOrganizationService {
    OrganizationProfileResponse getProfileForOrg(UUID orgId);
    OrganizationProfileResponse updateOrgProfile(UUID orgId, UpdateOrgProfileRequest request);
}
