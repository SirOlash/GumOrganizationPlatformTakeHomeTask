package com.Semicolon.service.profile;

import com.Semicolon.dtos.request.UpdateOrgProfileRequest;
import com.Semicolon.dtos.request.UpdatePlanRequest;
import com.Semicolon.dtos.response.OrganizationProfileResponse;
import com.Semicolon.dtos.response.UpdatePlanResponse;

import java.util.UUID;

public interface IOrganizationService {
    OrganizationProfileResponse getProfileForOrg(UUID orgId);
    OrganizationProfileResponse updateOrgProfile(UUID orgId, UpdateOrgProfileRequest request);
    UpdatePlanResponse changePlan(UUID userId, UpdatePlanRequest request);
}
