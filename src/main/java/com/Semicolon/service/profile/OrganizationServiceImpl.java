package com.Semicolon.service.profile;

import com.Semicolon.data.models.Organization;
import com.Semicolon.data.models.User;
import com.Semicolon.data.repositories.OrganizationRepository;
import com.Semicolon.data.repositories.UserRepository;
import com.Semicolon.dtos.request.UpdateOrgProfileRequest;
import com.Semicolon.dtos.response.OrganizationProfileResponse;
import com.Semicolon.exceptions.api.UserNotFoundException;
import com.Semicolon.service.utils.LogoValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrganizationServiceImpl implements IOrganizationService{

    private final UserRepository userRepo;
    private final OrganizationRepository orgRepo;
    private final LogoValidator logoValidator;
    private static final Logger log = LoggerFactory.getLogger(OrganizationServiceImpl.class);

    public OrganizationServiceImpl(UserRepository userRepo,
                                    OrganizationRepository orgRepo,
                                    LogoValidator logoValidator) {
        this.userRepo = userRepo;
        this.orgRepo = orgRepo;
        this.logoValidator = logoValidator;
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationProfileResponse getProfileForOrg(UUID UserId) {
        User user = userRepo.findById(UserId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + UserId));
        return buildProfileResponse(user.getOrganization());
    }

    @Override
    public OrganizationProfileResponse updateOrgProfile(UUID UserId, UpdateOrgProfileRequest request) {
        log.debug("updateOrgProfile called for user={}, incoming: logoUrl='{}' website='{}' industry='{}' description='{}'",
                UserId, request.getLogoUrl(), request.getWebsite(), request.getIndustry(), request.getDescription());
        User user = userRepo.findById(UserId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + UserId));
        Organization org = user.getOrganization();

        if (StringUtils.hasText(request.getLogoUrl())) {
            logoValidator.validateLogoUrl(request.getLogoUrl());
            org.setLogoUrl(request.getLogoUrl().trim());
        }
        if (StringUtils.hasText(request.getWebsite())) {
            org.setWebsite(request.getWebsite().trim());
        }
        if (StringUtils.hasText(request.getIndustry())) {
            org.setIndustry(request.getIndustry().trim());
        }
        if (StringUtils.hasText(request.getDescription())) {
            org.setDescription(request.getDescription().trim());
        }
        org.setUpdatedAt(Instant.now());
        orgRepo.save(org);
        return buildProfileResponse(org);
    }

    private OrganizationProfileResponse buildProfileResponse(Organization org) {
        OrganizationProfileResponse dto = new OrganizationProfileResponse();
                dto.setId(org.getId());
                dto.setName(org.getName());
                dto.setLogoUrl(org.getLogoUrl());
                dto.setWebsite(org.getWebsite());
                dto.setIndustry(org.getIndustry());
                dto.setDescription(org.getDescription());
                dto.setMembersCount(org.getMembersCount());
                dto.setProgramsCount(org.getProgramsCount());
                dto.setCreatedAt(org.getCreatedAt());
                dto.setUpdatedAt(org.getUpdatedAt());
                dto.setPlan(org.getPlan() == null ? null : org.getPlan());

        List<String> missing = new ArrayList<>();
        int total = 4;
        int filled = 0;
        if (StringUtils.hasText(org.getLogoUrl())) filled++; else missing.add("logoUrl");
        if (StringUtils.hasText(org.getWebsite())) filled++; else missing.add("website");
        if (StringUtils.hasText(org.getIndustry())) filled++; else missing.add("industry");
        if (StringUtils.hasText(org.getDescription())) filled++; else missing.add("description");

        int percent = Math.round((filled * 100f) / total);

        dto.setCompletenessPercent(percent);
        dto.setMissingFields(missing);
        return dto;
    }
}
