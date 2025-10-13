package com.Semicolon.service.profile;

import com.Semicolon.data.models.Organization;
import com.Semicolon.data.models.Plan;
import com.Semicolon.data.models.Role;
import com.Semicolon.data.models.User;
import com.Semicolon.data.repositories.OrganizationRepository;
import com.Semicolon.data.repositories.UserRepository;
import com.Semicolon.dtos.request.UpdateOrgProfileRequest;
import com.Semicolon.dtos.request.UpdatePlanRequest;
import com.Semicolon.dtos.response.OrganizationProfileResponse;
import com.Semicolon.dtos.response.UpdatePlanResponse;
import com.Semicolon.exceptions.api.InviteForbiddenException;
import com.Semicolon.exceptions.api.OrganizationNotFoundException;
import com.Semicolon.exceptions.api.PlanDowngradeBlockedException;
import com.Semicolon.exceptions.api.UserNotFoundException;
import com.Semicolon.service.invite.InviteServiceImpl;
import com.Semicolon.service.program.ProgramServiceImpl;
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

    private ProgramServiceImpl programService;
    private InviteServiceImpl inviteService;
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

    @Override
    @Transactional
    public UpdatePlanResponse changePlan(UUID userId, UpdatePlanRequest request){
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        Organization organization = user.getOrganization();
        if (organization == null) {
            throw new OrganizationNotFoundException("User is not attached to an organization");
        }
        UUID organizationId = organization.getId();

        Organization org = orgRepo.findByIdForUpdate(organizationId)
                .orElseThrow(() -> new OrganizationNotFoundException("Organization not found: " + organizationId));

        if (user.getRole() != Role.ADMIN){
            throw new InviteForbiddenException("Only organization admins may change plan");
        }
        Plan newPlan = request.getPlan();

        Plan oldPlan = org.getPlan();
        if (oldPlan == newPlan){
            return new UpdatePlanResponse(oldPlan, newPlan, Instant.now(), "no-op: same plan");
        }

        int targetMemberLimit = inviteService.planLimit(newPlan);
        int targetProgramLimit = programService.planLimit(newPlan);

        if (org.getMembersCount() > targetMemberLimit || org.getProgramsCount() > targetProgramLimit) {
            String details = String.format("members=%d/%d programs=%d/%d",
                    org.getMembersCount(), targetMemberLimit, org.getProgramsCount(), targetProgramLimit);
            throw new PlanDowngradeBlockedException(details);
        }
        org.setPlan(newPlan);
        org.setUpdatedAt(Instant.now());
        orgRepo.save(org);

        return new UpdatePlanResponse(oldPlan, newPlan, Instant.now(), "Plan updated");
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
