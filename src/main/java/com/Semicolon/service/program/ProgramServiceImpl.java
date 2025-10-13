package com.Semicolon.service.program;

import com.Semicolon.data.models.*;
import com.Semicolon.data.repositories.OrganizationRepository;
import com.Semicolon.data.repositories.ProgramRepository;
import com.Semicolon.data.repositories.UserRepository;
import com.Semicolon.dtos.request.ArchiveProgramRequest;
import com.Semicolon.dtos.request.CreateProgramRequest;
import com.Semicolon.dtos.request.FindProgramRequest;
import com.Semicolon.dtos.request.UpdateProgramRequest;
import com.Semicolon.dtos.response.ProgramResponse;
import com.Semicolon.dtos.response.ProgramsListResponse;
import com.Semicolon.exceptions.api.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ProgramServiceImpl implements IProgramService {

    private final ProgramRepository programRepo;
    private final OrganizationRepository orgRepo;
    private final UserRepository userRepo;

    public ProgramServiceImpl(ProgramRepository programRepo,
                              OrganizationRepository orgRepo,
                              UserRepository userRepo) {
        this.programRepo = programRepo;
        this.orgRepo = orgRepo;
        this.userRepo = userRepo;
    }

    @Override
    @Transactional
    public ProgramResponse createProgram(UUID userId, CreateProgramRequest request) {
        validateDates(request.getStartAt(), request.getEndAt());

        if (!StringUtils.hasText(request.getName())) {
            throw new NameValidationException("name is required");
        }
        String name = request.getName().trim();

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        Organization organization = user.getOrganization();
        if (organization == null) {
            throw new OrganizationNotFoundException("User is not attached to an organization");
        }
        UUID organizationId = organization.getId();

        if (!(user.getRole() == Role.ADMIN || user.getRole() == Role.MANAGER)) {
            throw new InviteForbiddenException("only Admin or Manager can create programs");
        }

        Organization org = orgRepo.findByIdForUpdate(organizationId)
                .orElseThrow(() -> new OrganizationNotFoundException("Organization not found: " + organizationId));

        int limit = planLimit(org.getPlan());
        if (org.getProgramsCount() >= limit) {
            throw new LimitExceededException("Plan limit exceeded", "programs", "limit");
        }

        if (programRepo.existsByOrganizationAndNameIgnoreCase(org, name)) {
            throw new ProgramConflictException("program name already exists in this organization");
        }

        Program p = new Program();
        p.setOrganization(org);
        p.setName(name);
        p.setDescription(StringUtils.hasText(request.getDescription()) ? request.getDescription().trim() : null);
        p.setStartAt(request.getStartAt());
        p.setEndAt(request.getEndAt());
        p.setStatus(ProgramStatus.ACTIVE);

        // save + handle concurrent name-creation races via DB unique constraint -> DataIntegrityViolationException
        try {
            programRepo.save(p);

            org.setProgramsCount(org.getProgramsCount() + 1);
            orgRepo.save(org);

        } catch (DataIntegrityViolationException dive) {
            // another transaction probably created the same-name program concurrently
            throw new ProgramConflictException("program name already exists in this organization");
        }

        return toDto(p);
    }

    @Override
    @Transactional
    public ProgramResponse updateProgram(UUID userId, UUID programId, UpdateProgramRequest request) {
        validateDates(request.getStartAt(), request.getEndAt());

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        Organization org = user.getOrganization();
        if (org == null) throw new OrganizationNotFoundException("User is not attached to an organization");

        Program p = programRepo.findByIdAndOrganization_Id(programId, org.getId())
                .orElseThrow(() -> new ProgramNotFoundException("Program not found"));

        if (!(user.getRole() == Role.ADMIN || user.getRole() == Role.MANAGER)) {
            throw new InviteForbiddenException("only Admin or Manager can update programs");
        }

        if (StringUtils.hasText(request.getName())) {
            String newName = request.getName().trim();
            if (!newName.equalsIgnoreCase(p.getName())
                    && programRepo.existsByOrganizationAndNameIgnoreCase(org, newName)) {
                throw new ProgramConflictException("program name already exists in this organization");
            }
            p.setName(newName);
        }
        if (request.getDescription() != null) p.setDescription(request.getDescription().trim());
        if (request.getStartAt() != null) p.setStartAt(request.getStartAt());
        if (request.getEndAt() != null) p.setEndAt(request.getEndAt());

        programRepo.save(p);
        return toDto(p);
    }

    @Override
    @Transactional(readOnly = true)
    public ProgramResponse getProgram(UUID userId, UUID programId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        Organization organization = user.getOrganization();
        if (organization == null) {
            throw new OrganizationNotFoundException("User is not attached to an organization");
        }
        UUID organizationId = organization.getId();

        Program p = programRepo.findByIdAndOrganization_Id(programId, organizationId)
                .orElseThrow(() -> new ProgramNotFoundException("Program not found"));
        if (p.getStatus() == ProgramStatus.ARCHIVED) {
            throw new ProgramNotFoundException("Program is archived");
        }
        return toDto(p);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProgramsListResponse> listPrograms(UUID userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        Organization organization = user.getOrganization();
        if (organization == null) {
            throw new OrganizationNotFoundException("User is not attached to an organization");
        }
        UUID organizationId = organization.getId();

        List<Program> programs = programRepo.findByOrganization_Id(organizationId);
        List<ProgramsListResponse> summaries = new ArrayList<>(programs.size());

        for (Program p : programs) {
            if (p.getStatus() == ProgramStatus.ACTIVE) {
                summaries.add(new ProgramsListResponse(p.getName(), p.getId()));
            }
        }
        return summaries;
    }

    @Override
    @Transactional
    public ProgramResponse archiveProgram(UUID userId, ArchiveProgramRequest request) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        Organization organization = user.getOrganization();
        if (organization == null) {
            throw new OrganizationNotFoundException("User is not attached to an organization");
        }
        UUID organizationId = organization.getId();

        if (!(user.getRole() == Role.ADMIN || user.getRole() == Role.MANAGER)) {
            throw new InviteForbiddenException("only Admin or Manager can update programs");
        }

        Organization org = orgRepo.findByIdForUpdate(organizationId)
                .orElseThrow(() -> new OrganizationNotFoundException("Organization not found: " + organizationId));

        if (!StringUtils.hasText(request.getProgramName())) {
            throw new ProgramNotFoundException("programName is required");
        }
        String programName = request.getProgramName().trim();

        Program p = programRepo.findByNameIgnoreCaseAndOrganization_Id(programName, organizationId)
                .orElseThrow(() -> new ProgramNotFoundException("Program not found"));

        if (p.getStatus() == ProgramStatus.ARCHIVED) {
            return toDto(p);
        }

        p.setStatus(ProgramStatus.ARCHIVED);
        programRepo.save(p);

        int current = org.getProgramsCount();
        org.setProgramsCount(Math.max(0, current - 1));
        orgRepo.save(org);

        return toDto(p);
    }

    private void validateDates(Instant start, Instant end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new DateValidationException("endAt must be after startAt");
        }
    }

    public int planLimit(Plan plan) {
        if (plan == null) return Integer.MAX_VALUE;
        switch (plan) {
            case FREE:
                return 3;
            case PRO:
                return 20;
            case ENTERPRISE:
            default:
                return Integer.MAX_VALUE;
        }
    }

    private ProgramResponse toDto(Program p) {
        ProgramResponse r = new ProgramResponse();
        r.setId(p.getId());
        r.setOrganizationId(p.getOrganization() != null ? p.getOrganization().getId() : null);
        r.setName(p.getName());
        r.setDescription(p.getDescription());
        r.setStatus(p.getStatus());
        r.setStartAt(p.getStartAt());
        r.setEndAt(p.getEndAt());
        r.setCreatedAt(p.getCreatedAt());
        r.setUpdatedAt(p.getUpdatedAt());
        return r;
    }
}
