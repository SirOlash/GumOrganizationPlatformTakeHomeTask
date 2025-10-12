package com.Semicolon.service.profile;

import com.Semicolon.data.models.Organization;
import com.Semicolon.data.models.User;
import com.Semicolon.data.repositories.OrganizationRepository;
import com.Semicolon.data.repositories.UserRepository;
import com.Semicolon.dtos.request.UpdateOrgProfileRequest;
import com.Semicolon.dtos.response.OrganizationProfileResponse;
import com.Semicolon.exceptions.api.InvalidLogoException;
import com.Semicolon.exceptions.api.UserNotFoundException;
import com.Semicolon.service.utils.LogoValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceImplTest {

    @Mock
    private UserRepository userRepo;

    @Mock
    private OrganizationRepository orgRepo;

    @Mock
    private LogoValidator logoValidator;

    @InjectMocks
    private OrganizationServiceImpl orgService;

    private UUID userId;
    private User user;
    private Organization org;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        org = new Organization();
        org.setId(UUID.randomUUID());
        org.setName("Acme Inc.");
        // start with empty profile fields
        org.setLogoUrl(null);
        org.setWebsite(null);
        org.setIndustry(null);
        org.setDescription(null);
        org.setMembersCount(3);
        org.setProgramsCount(2);
        org.setCreatedAt(Instant.now());

        user = new User();
        user.setId(userId);
        user.setEmail("alice@example.com");
        user.setPasswordHash("hash");
        user.setOrganization(org);
    }

    @Test
    void getProfileForOrg_success_completenessAndMissingFields() {
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));

        OrganizationProfileResponse resp = orgService.getProfileForOrg(userId);

        assertThat(resp).isNotNull();
        assertThat(resp.getId()).isEqualTo(org.getId());
        // initially all 4 profile fields are missing => completeness 0
        assertThat(resp.getCompletenessPercent()).isEqualTo(0);
        assertThat(resp.getMissingFields()).containsExactlyInAnyOrder("logoUrl", "website", "industry", "description");

        verify(userRepo, times(1)).findById(userId);
    }

    @Test
    void getProfileForOrg_userNotFound_throws() {
        when(userRepo.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orgService.getProfileForOrg(userId))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining(userId.toString());

        verify(userRepo, times(1)).findById(userId);
    }

    @Test
    void upsertProfile_updatesFields_and_returnsCompleteProfile() {
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        // logo validator should accept the URL
        when(logoValidator.validateLogoUrl("https://cdn.example.com/logo.png")).thenReturn(true);
        // orgRepo.save should return the saved entity (simulate JPA)
        when(orgRepo.save(any(Organization.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateOrgProfileRequest req = new UpdateOrgProfileRequest();
        req.setLogoUrl("https://cdn.example.com/logo.png");
        req.setWebsite("https://acme.example.com");
        req.setIndustry("Technology");
        req.setDescription("Acme does things.");

        OrganizationProfileResponse resp = orgService.updateOrgProfile(userId, req);

        assertThat(resp).isNotNull();
        assertThat(resp.getLogoUrl()).isEqualTo(req.getLogoUrl());
        assertThat(resp.getWebsite()).isEqualTo(req.getWebsite());
        assertThat(resp.getIndustry()).isEqualTo(req.getIndustry());
        assertThat(resp.getDescription()).isEqualTo(req.getDescription());
        // all 4 fields provided -> completeness 100
        assertThat(resp.getCompletenessPercent()).isEqualTo(100);
        assertThat(resp.getMissingFields()).isEmpty();

        // verify save was called and updatedAt was set
        ArgumentCaptor<Organization> captor = ArgumentCaptor.forClass(Organization.class);
        verify(orgRepo, times(1)).save(captor.capture());
        Organization saved = captor.getValue();
        assertThat(StringUtils.hasText(saved.getLogoUrl())).isTrue();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void upsertProfile_invalidLogo_throwsAndDoesNotSave() {
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        // make validator throw
        when(logoValidator.validateLogoUrl("bad-url")).thenThrow(new InvalidLogoException("bad logo"));

        UpdateOrgProfileRequest req = new UpdateOrgProfileRequest();
        req.setLogoUrl("bad-url");

        assertThatThrownBy(() -> orgService.updateOrgProfile(userId, req))
                .isInstanceOf(InvalidLogoException.class)
                .hasMessageContaining("bad logo");

        verify(orgRepo, never()).save(any());
    }
}
