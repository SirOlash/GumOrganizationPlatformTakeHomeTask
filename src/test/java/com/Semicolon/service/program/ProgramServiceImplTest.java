package com.Semicolon.service.program;

import com.Semicolon.data.models.*;
import com.Semicolon.data.repositories.OrganizationRepository;
import com.Semicolon.data.repositories.ProgramRepository;
import com.Semicolon.data.repositories.UserRepository;
import com.Semicolon.dtos.request.ArchiveProgramRequest;
import com.Semicolon.dtos.request.CreateProgramRequest;
import com.Semicolon.dtos.request.UpdateProgramRequest;
import com.Semicolon.dtos.response.ProgramResponse;
import com.Semicolon.dtos.response.ProgramsListResponse;
import com.Semicolon.exceptions.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class ProgramServiceImplTest {

    @Mock
    ProgramRepository programRepo;

    @Mock
    OrganizationRepository orgRepo;

    @Mock
    UserRepository userRepo;

    @InjectMocks
    ProgramServiceImpl service;

    UUID userId;
    UUID orgId;
    User adminUser;
    Organization org;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        orgId = UUID.randomUUID();

        org = new Organization();
        org.setId(orgId);
        org.setName("acme");
        org.setPlan(Plan.FREE);
        org.setProgramsCount(0);
        org.setCreatedAt(Instant.now());

        adminUser = new User();
        adminUser.setId(userId);
        adminUser.setEmail("admin@acme.test");
        adminUser.setRole(Role.ADMIN);
        adminUser.setStatus(UserStatus.VERIFIED);
        adminUser.setOrganization(org);
    }

    @Test
    @DisplayName("createProgram: success - saves program and increments org count")
    void createProgram_success_incrementsCount() {
        CreateProgramRequest req = new CreateProgramRequest(
                "New Program",
                "desc",
                null,
                null
        );

        when(userRepo.findById(userId)).thenReturn(Optional.of(adminUser));
        when(orgRepo.findByIdForUpdate(orgId)).thenReturn(Optional.of(org));
        when(programRepo.existsByOrganizationAndNameIgnoreCase(org, "New Program")).thenReturn(false);
        // simulate save returns same program with id set
        doAnswer(inv -> {
            Program p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        }).when(programRepo).save(any(Program.class));
        when(orgRepo.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));

        ProgramResponse resp = service.createProgram(userId, req);

        assertThat(resp).isNotNull();
        assertThat(resp.getName()).isEqualTo("New Program");
        // verify program saved
        ArgumentCaptor<Program> pc = ArgumentCaptor.forClass(Program.class);
        verify(programRepo, times(1)).save(pc.capture());
        Program saved = pc.getValue();
        assertThat(saved.getName()).isEqualTo("New Program");
        // verify org count increment
        ArgumentCaptor<Organization> oc = ArgumentCaptor.forClass(Organization.class);
        verify(orgRepo, times(1)).save(oc.capture());
        Organization savedOrg = oc.getValue();
        assertThat(savedOrg.getProgramsCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("createProgram: fails when plan limit reached")
    void createProgram_fails_whenPlanLimitExceeded() {
        CreateProgramRequest req = new CreateProgramRequest("P", null, null, null);

        org.setProgramsCount(3); // FREE limit = 3
        when(userRepo.findById(userId)).thenReturn(Optional.of(adminUser));
        when(orgRepo.findByIdForUpdate(orgId)).thenReturn(Optional.of(org));

        assertThatThrownBy(() -> service.createProgram(userId, req))
                .isInstanceOf(LimitExceededException.class);

        verify(programRepo, never()).save(any());
    }

    @Test
    @DisplayName("createProgram: concurrent save causes ProgramConflictException")
    void createProgram_conflict_on_concurrent_save_throwsProgramConflictException() {
        CreateProgramRequest req = new CreateProgramRequest("Concurrent", null, null, null);

        when(userRepo.findById(userId)).thenReturn(Optional.of(adminUser));
        when(orgRepo.findByIdForUpdate(orgId)).thenReturn(Optional.of(org));
        when(programRepo.existsByOrganizationAndNameIgnoreCase(org, "Concurrent")).thenReturn(false);

        // simulate DB unique constraint violation during save
        doThrow(new DataIntegrityViolationException("unique constraint")).when(programRepo).save(any(Program.class));

        assertThatThrownBy(() -> service.createProgram(userId, req))
                .isInstanceOf(ProgramConflictException.class);

        verify(orgRepo, never()).save(argThat(o -> o.getProgramsCount() > 0)); // org count not incremented
    }

    @Test
    @DisplayName("createProgram: invalid dates -> DateValidationException")
    void createProgram_invalidDates_throwsDateValidationException() {
        Instant now = Instant.now();
        CreateProgramRequest req = new CreateProgramRequest("BadDates", null, now.plusSeconds(3600), now);

        assertThatThrownBy(() -> service.createProgram(userId, req))
                .isInstanceOf(DateValidationException.class);

        verify(programRepo, never()).save(any());
    }

    @Test
    @DisplayName("updateProgram: success - rename and update fields")
    void updateProgram_success_changesNameAndDescription() {
        UUID programId = UUID.randomUUID();
        Program existing = new Program();
        existing.setId(programId);
        existing.setOrganization(org);
        existing.setName("Old");
        existing.setStatus(ProgramStatus.ACTIVE);

        UpdateProgramRequest req = new UpdateProgramRequest("New", "newdesc", null, null, null);

        when(userRepo.findById(userId)).thenReturn(Optional.of(adminUser));
        when(programRepo.findByIdAndOrganization_Id(programId, orgId)).thenReturn(Optional.of(existing));
        when(programRepo.existsByOrganizationAndNameIgnoreCase(org, "New")).thenReturn(false);
        when(programRepo.save(any(Program.class))).thenAnswer(inv -> inv.getArgument(0));

        ProgramResponse out = service.updateProgram(userId, programId, req);

        assertThat(out).isNotNull();
        assertThat(out.getName()).isEqualTo("New");
        assertThat(out.getDescription()).isEqualTo("newdesc");

        verify(programRepo, times(1)).save(any(Program.class));
    }

    @Test
    @DisplayName("updateProgram: rename to existing name -> conflict")
    void updateProgram_renameConflict_throwsProgramConflictException() {
        UUID programId = UUID.randomUUID();
        Program existing = new Program();
        existing.setId(programId);
        existing.setOrganization(org);
        existing.setName("Old");

        UpdateProgramRequest req = new UpdateProgramRequest("ExistingName", null, null, null, null);

        when(userRepo.findById(userId)).thenReturn(Optional.of(adminUser));
        when(programRepo.findByIdAndOrganization_Id(programId, orgId)).thenReturn(Optional.of(existing));
        when(programRepo.existsByOrganizationAndNameIgnoreCase(org, "ExistingName")).thenReturn(true);

        assertThatThrownBy(() -> service.updateProgram(userId, programId, req))
                .isInstanceOf(ProgramConflictException.class);

        verify(programRepo, never()).save(any());
    }

    @Test
    @DisplayName("archiveProgram: success decrements programsCount and is idempotent")
    void archiveProgram_success_and_idempotent() {
        String name = "ToArchive";
        ArchiveProgramRequest req = new ArchiveProgramRequest(name);

        Program active = new Program();
        active.setId(UUID.randomUUID());
        active.setOrganization(org);
        active.setName(name);
        active.setStatus(ProgramStatus.ACTIVE);

        when(userRepo.findById(userId)).thenReturn(Optional.of(adminUser));
        when(orgRepo.findByIdForUpdate(orgId)).thenReturn(Optional.of(org));
        when(programRepo.findByNameIgnoreCaseAndOrganization_Id(name, orgId)).thenReturn(Optional.of(active));
        when(programRepo.save(any(Program.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orgRepo.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));

        ProgramResponse resp1 = service.archiveProgram(userId, req);

        assertThat(resp1).isNotNull();
        assertThat(resp1.getStatus()).isEqualTo(ProgramStatus.ARCHIVED);

        // org count should have been decremented once
        assertThat(org.getProgramsCount()).isEqualTo(0);

        // now simulate idempotent second call: program already archived
        Program archived = new Program();
        archived.setId(active.getId());
        archived.setOrganization(org);
        archived.setName(name);
        archived.setStatus(ProgramStatus.ARCHIVED);

        when(programRepo.findByNameIgnoreCaseAndOrganization_Id(name, orgId)).thenReturn(Optional.of(archived));

        ProgramResponse resp2 = service.archiveProgram(userId, req);
        assertThat(resp2).isNotNull();
        assertThat(resp2.getStatus()).isEqualTo(ProgramStatus.ARCHIVED);

        // org count should not go negative or change on second call
        assertThat(org.getProgramsCount()).isEqualTo(0);

        verify(programRepo, atLeastOnce()).save(any(Program.class));
        verify(orgRepo, atLeastOnce()).save(any(Organization.class));
    }
}
