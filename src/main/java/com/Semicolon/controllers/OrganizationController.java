package com.Semicolon.controllers;

import com.Semicolon.dtos.request.UpdateOrgProfileRequest;
import com.Semicolon.dtos.request.UpdatePlanRequest;
import com.Semicolon.dtos.response.OrganizationProfileResponse;
import com.Semicolon.dtos.response.UpdatePlanResponse;
import com.Semicolon.service.profile.IOrganizationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/org")
public class OrganizationController {
    private static final Logger log = LoggerFactory.getLogger(OrganizationController.class);
    private final IOrganizationService organizationService;

    public OrganizationController(IOrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping("/me")
    public ResponseEntity<OrganizationProfileResponse> getProfileForOrg(@AuthenticationPrincipal UUID userId) {
        if (userId == null) return ResponseEntity.status(401).build();
        OrganizationProfileResponse response = organizationService.getProfileForOrg(userId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping(value = "/update-org-profile", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrganizationProfileResponse> updateOrgProfile(@AuthenticationPrincipal UUID userId, @Valid @RequestBody UpdateOrgProfileRequest request) {
        if (userId == null) return ResponseEntity.status(401).build();
        log.debug("UpdateOrgProfileRequest received: {}", request);
        OrganizationProfileResponse response = organizationService.updateOrgProfile(userId, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/update-plan")
    public ResponseEntity<UpdatePlanResponse> changePlan(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody UpdatePlanRequest request) {
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        UpdatePlanResponse resp = organizationService.changePlan(userId, request);
        return ResponseEntity.ok(resp);
    }

}
