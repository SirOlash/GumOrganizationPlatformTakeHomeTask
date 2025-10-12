package com.Semicolon.service.auth;

import com.Semicolon.data.models.Organization;
import com.Semicolon.data.models.User;
import com.Semicolon.data.models.UserStatus;
import com.Semicolon.data.models.VerificationToken;
import com.Semicolon.data.repositories.OrganizationRepository;
import com.Semicolon.data.repositories.UserRepository;
import com.Semicolon.data.repositories.VerificationTokenRepository;
import com.Semicolon.dtos.request.AdminRegistrationRequest;
import com.Semicolon.dtos.response.AdminRegistrationResponse;
import com.Semicolon.exceptions.api.EmailInUseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private VerificationTokenRepository tokenRepo;

    @Mock
    private OrganizationRepository orgRepo;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private RegistrationServiceImpl registrationService;

    private AdminRegistrationRequest req;

    @BeforeEach
    void setUp() {
        req = new AdminRegistrationRequest();
        req.setEmail("alice@example.com");
        req.setPassword("plaintextpw");
        req.setOrganizationName("AcmeCo");
    }

    @Test
    void register_newUser_createsOrgUserAndToken() {
        when(userRepository.findByEmail(req.getEmail())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(req.getPassword())).thenReturn("hashed_pw");

        ArgumentCaptor<Organization> orgCaptor = ArgumentCaptor.forClass(Organization.class);
        when(orgRepo.save(orgCaptor.capture())).thenAnswer(invocation -> {
            Organization o = invocation.getArgument(0);
            return o;
        });

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(userCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<VerificationToken> tokenCaptor = ArgumentCaptor.forClass(VerificationToken.class);
        when(tokenRepo.save(tokenCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        AdminRegistrationResponse resp = registrationService.register(req);
        Organization savedOrg = orgCaptor.getValue();
        assertEquals("AcmeCo", savedOrg.getName());
        assertEquals(1, savedOrg.getMembersCount(), "membersCount should be 1 for the admin");

        User savedUser = userCaptor.getValue();
        assertEquals(req.getEmail(), savedUser.getEmail());
        assertEquals(savedOrg, savedUser.getOrganization());

        assertEquals("hashed_pw", savedUser.getPasswordHash());

        VerificationToken savedToken = tokenCaptor.getValue();
        assertNotNull(savedToken.getToken());
        assertEquals(savedUser, savedToken.getUser());

        assertNotNull(resp.getToken());
        assertTrue(resp.getMessage().toLowerCase().contains("registration"), "expected success message");
    }

    @Test
    void register_existingVerifiedUser_throwsEmailInUse() {
        User existing = new User();
        existing.setEmail(req.getEmail());
        existing.setStatus(UserStatus.VERIFIED);

        when(userRepository.findByEmail(req.getEmail())).thenReturn(Optional.of(existing));

        assertThrows(EmailInUseException.class, () -> registrationService.register(req));

        verify(orgRepo, never()).save(any());
        verify(userRepository, never()).save(any());
        verify(tokenRepo, never()).save(any());
    }

    @Test
    void register_existingUnverified_resendsToken() {
        User existing = new User();
        existing.setEmail(req.getEmail());
        existing.setStatus(UserStatus.PENDING);

        when(userRepository.findByEmail(req.getEmail())).thenReturn(Optional.of(existing));

        ArgumentCaptor<VerificationToken> tokenCaptor = ArgumentCaptor.forClass(VerificationToken.class);
        when(tokenRepo.save(tokenCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        AdminRegistrationResponse resp = registrationService.register(req);

        verify(tokenRepo, times(1)).save(any());
        VerificationToken savedToken = tokenCaptor.getValue();
        assertNotNull(savedToken.getToken());
        assertEquals(existing, savedToken.getUser());

        assertEquals("VERIFICATION_RESENT", resp.getMessage());
        assertEquals(savedToken.getToken(), resp.getToken());
    }
}
