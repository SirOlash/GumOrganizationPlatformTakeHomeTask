package com.Semicolon.service.invite;

import com.Semicolon.data.models.*;
import com.Semicolon.data.repositories.InviteRepository;
import com.Semicolon.data.repositories.OrganizationRepository;
import com.Semicolon.data.repositories.OutboxEmailRepository;
import com.Semicolon.data.repositories.UserRepository;
import com.Semicolon.dtos.request.AcceptInviteRequest;
import com.Semicolon.dtos.request.CreateInviteRequest;
import com.Semicolon.dtos.response.AcceptInviteResponse;
import com.Semicolon.dtos.response.InviteListItem;
import com.Semicolon.dtos.response.InviteResponse;
import com.Semicolon.exceptions.api.*;
import com.Semicolon.service.utils.OutboxWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InviteServiceImplTest {

    @Mock
    InviteRepository inviteRepo;

    @Mock
    UserRepository userRepo;

    @Mock
    OrganizationRepository orgRepo;

    @Mock
    OutboxEmailRepository outboxRepo;

    @Mock
    OutboxWorker outboxWorker;

    @InjectMocks
    InviteServiceImpl service;

    private UUID adminId;
    private User admin;
    private Organization org;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        org = new Organization();
        org.setId(UUID.randomUUID());
        org.setName("Acme");
        org.setMembersCount(1);
        org.setProgramsCount(0);
        org.setPlan(Plan.FREE);
        org.setCreatedAt(Instant.now());

        admin = new User();
        admin.setId(adminId);
        admin.setEmail("admin@acme.test");
        admin.setPasswordHash("hash");
        admin.setOrganization(org);
        admin.setRole(Role.ADMIN);
        admin.setStatus(UserStatus.VERIFIED);

        // lenient default save behaviour to simulate JPA: return argument and set createdAt on Invite
        Mockito.lenient().when(inviteRepo.save(any(Invite.class))).thenAnswer(invocation -> {
            Invite i = invocation.getArgument(0);
            if (i.getCreatedAt() == null) i.setCreatedAt(Instant.now());
            return i;
        });
        Mockito.lenient().when(outboxRepo.save(any(OutboxEmail.class))).thenAnswer(invocation -> {
            OutboxEmail o = invocation.getArgument(0);
            return o;
        });
        Mockito.lenient().when(userRepo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.lenient().when(orgRepo.save(any(Organization.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createInvite_success_whenOutboxSent_returnsInviteResponse() {
        // arrange
        CreateInviteRequest req = new CreateInviteRequest("new@x.com", Role.MEMBER);

        when(userRepo.findById(adminId)).thenReturn(Optional.of(admin));
        when(userRepo.findByEmail("new@x.com")).thenReturn(Optional.empty());
        when(inviteRepo.existsByEmailIgnoreCaseAndOrganization_IdAndStatusIn(eq("new@x.com"), eq(org.getId()), anyList())).thenReturn(false);

        // simulate immediate send success
        when(outboxWorker.processOutboxAttempt(any())).thenReturn(OutboxStatus.SENT);

        // sanity: ensure service is present
        assertThat(service).as("service should be injected").isNotNull();

        // act
        InviteResponse resp = service.createInvite(adminId, req);

        // assert
        assertThat(resp).isNotNull();
        assertThat(resp.getEmail()).isEqualTo("new@x.com");
        assertThat(resp.getRole()).isEqualTo(Role.MEMBER);
        assertThat(resp.getToken()).isNotBlank();
        assertThat(resp.getCreatedAt()).isNotNull();
        assertThat(resp.getExpiresAt()).isNotNull();

        // verify persistence and outbox interactions
        verify(inviteRepo, atLeastOnce()).save(any(Invite.class));
        verify(outboxRepo, times(1)).save(any(OutboxEmail.class));
        verify(outboxWorker, times(1)).processOutboxAttempt(any());
    }

    @Test
    void createInvite_conflict_whenUserAlreadyBelongsToOrg() {
        CreateInviteRequest req = new CreateInviteRequest("member@x.com", Role.MEMBER);
        when(userRepo.findById(adminId)).thenReturn(Optional.of(admin));

        User existing = new User();
        existing.setId(UUID.randomUUID());
        existing.setEmail("member@x.com");
        existing.setOrganization(new Organization()); // already in some org

        when(userRepo.findByEmail("member@x.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createInvite(adminId, req))
                .isInstanceOf(InviteConflictException.class)
                .hasMessageContaining("user already belongs");
    }

    @Test
    void createInvite_limitExceeded_throwsLimitExceeded() {
        CreateInviteRequest req = new CreateInviteRequest("someone@x.com", Role.MEMBER);
        when(userRepo.findById(adminId)).thenReturn(Optional.of(admin));

        // set org to be at FREE plan limit (5)
        org.setMembersCount(5);
        org.setPlan(Plan.FREE);

        assertThatThrownBy(() -> service.createInvite(adminId, req))
                .isInstanceOf(LimitExceededException.class)
                .hasMessageContaining("plan members limit");
    }

    @Test
    void getInvitesForUser_returnsList() {
        UUID userId = UUID.randomUUID();
        User u = new User();
        u.setId(userId);
        u.setEmail("invitee@x.com");

        Invite inv = new Invite();
        inv.setId(UUID.randomUUID());
        inv.setEmail("invitee@x.com");
        inv.setToken("tok-1");
        inv.setRole(Role.MEMBER);
        inv.setStatus(InviteStatus.ISSUED);
        inv.setOrganization(org);
        inv.setCreatedAt(Instant.now());
        inv.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));

        when(userRepo.findById(userId)).thenReturn(Optional.of(u));
        when(inviteRepo.findByEmailIgnoreCaseAndStatusIn(eq("invitee@x.com"), anyList())).thenReturn(List.of(inv));

        List<InviteListItem> items = service.getInvitesForUser(userId);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getToken()).isEqualTo("tok-1");
    }

    @Test
    void acceptInvite_success_marksUsed_and_assignsUser() {
        UUID userId = UUID.randomUUID();
        User u = new User();
        u.setId(userId);
        u.setEmail("joe@x.com");
        u.setPasswordHash("hash");
        u.setStatus(UserStatus.VERIFIED);
        u.setRole(Role.MEMBER);
        u.setOrganization(null); // not in org

        Invite inv = new Invite();
        inv.setId(UUID.randomUUID());
        inv.setToken("invite-token");
        inv.setEmail("joe@x.com");
        inv.setRole(Role.MEMBER);
        inv.setStatus(InviteStatus.ISSUED);
        inv.setOrganization(org);
        inv.setExpiresAt(Instant.now().plus(2, ChronoUnit.DAYS));

        when(userRepo.findById(userId)).thenReturn(Optional.of(u));
        when(inviteRepo.findByToken("invite-token")).thenReturn(Optional.of(inv));
        when(orgRepo.findByIdForUpdate(org.getId())).thenReturn(Optional.of(org));
        // simulate save returns
        when(inviteRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(orgRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        AcceptInviteRequest req = new AcceptInviteRequest("invite-token");
        AcceptInviteResponse resp = service.acceptInvite(userId, req);

        assertThat(resp).isNotNull();
        assertThat(resp.getCode()).isEqualTo("INVITE_ACCEPTED");

        // verify invite status updated
        ArgumentCaptor<Invite> invCap = ArgumentCaptor.forClass(Invite.class);
        verify(inviteRepo, atLeastOnce()).save(invCap.capture());
        Invite afterInvite = invCap.getValue();
        assertThat(afterInvite.getStatus()).isEqualTo(InviteStatus.USED);

        // verify user assigned to org
        ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
        verify(userRepo, atLeastOnce()).save(userCap.capture());
        User savedUser = userCap.getValue();
        assertThat(savedUser.getOrganization()).isNotNull();
        assertThat(savedUser.getOrganization().getId()).isEqualTo(org.getId());

        // verify org member count incremented
        ArgumentCaptor<Organization> orgCap = ArgumentCaptor.forClass(Organization.class);
        verify(orgRepo, atLeastOnce()).save(orgCap.capture());
        Organization savedOrg = orgCap.getValue();
        assertThat(savedOrg.getMembersCount()).isEqualTo(2); // was 1, now 2
    }

    @Test
    void acceptInvite_tokenInvalid_throwsNotFound() {
        UUID userId = UUID.randomUUID();
        User u = new User();
        u.setId(userId);
        u.setEmail("joe@x.com");
        u.setStatus(UserStatus.VERIFIED);

        when(userRepo.findById(userId)).thenReturn(Optional.of(u));
        when(inviteRepo.findByToken("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acceptInvite(userId, new AcceptInviteRequest("bad")))
                .isInstanceOf(InviteNotFoundException.class);
    }

    @Test
    void acceptInvite_tokenExpired_marksExpiredAndThrows() {
        UUID userId = UUID.randomUUID();
        User u = new User();
        u.setId(userId);
        u.setEmail("sue@x.com");
        u.setStatus(UserStatus.VERIFIED);

        Invite inv = new Invite();
        inv.setId(UUID.randomUUID());
        inv.setToken("expired-token");
        inv.setEmail("sue@x.com");
        inv.setStatus(InviteStatus.ISSUED);
        inv.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));

        when(userRepo.findById(userId)).thenReturn(Optional.of(u));
        when(inviteRepo.findByToken("expired-token")).thenReturn(Optional.of(inv));

        assertThatThrownBy(() -> service.acceptInvite(userId, new AcceptInviteRequest("expired-token")))
                .isInstanceOf(InviteNotFoundException.class);

        // verify invite status updated to EXPIRED
        ArgumentCaptor<Invite> cap = ArgumentCaptor.forClass(Invite.class);
        verify(inviteRepo, atLeastOnce()).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(InviteStatus.EXPIRED);
    }

    @Test
    void acceptInvite_wrongUser_throwsForbidden() {
        UUID userId = UUID.randomUUID();
        User u = new User();
        u.setId(userId);
        u.setEmail("joe@x.com");
        u.setStatus(UserStatus.VERIFIED);

        Invite inv = new Invite();
        inv.setToken("t");
        inv.setEmail("someoneelse@x.com");
        inv.setStatus(InviteStatus.ISSUED);
        inv.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));

        when(userRepo.findById(userId)).thenReturn(Optional.of(u));
        when(inviteRepo.findByToken("t")).thenReturn(Optional.of(inv));

        assertThatThrownBy(() -> service.acceptInvite(userId, new AcceptInviteRequest("t")))
                .isInstanceOf(InviteForbiddenException.class);
    }

    @Test
    void acceptInvite_orgLimitExceeded_throwsLimitExceeded() {
        UUID userId = UUID.randomUUID();
        User u = new User();
        u.setId(userId);
        u.setEmail("joe@x.com");
        u.setStatus(UserStatus.VERIFIED);

        Invite inv = new Invite();
        inv.setToken("t2");
        inv.setEmail("joe@x.com");
        inv.setStatus(InviteStatus.ISSUED);
        inv.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
        inv.setOrganization(org);

        // set org at limit for FREE
        org.setMembersCount(5);
        org.setPlan(Plan.FREE);

        when(userRepo.findById(userId)).thenReturn(Optional.of(u));
        when(inviteRepo.findByToken("t2")).thenReturn(Optional.of(inv));
        when(orgRepo.findByIdForUpdate(org.getId())).thenReturn(Optional.of(org));

        assertThatThrownBy(() -> service.acceptInvite(userId, new AcceptInviteRequest("t2")))
                .isInstanceOf(LimitExceededException.class);
    }

    @Test
    void acceptInvite_userAlreadyInOtherOrg_throwsConflict() {
        UUID userId = UUID.randomUUID();
        User u = new User();
        u.setId(userId);
        u.setEmail("joe@x.com");
        u.setStatus(UserStatus.VERIFIED);

        Organization other = new Organization();
        other.setId(UUID.randomUUID());
        u.setOrganization(other);

        Invite inv = new Invite();
        inv.setToken("t3");
        inv.setEmail("joe@x.com");
        inv.setStatus(InviteStatus.ISSUED);
        inv.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
        inv.setOrganization(org);

        when(userRepo.findById(userId)).thenReturn(Optional.of(u));
        when(inviteRepo.findByToken("t3")).thenReturn(Optional.of(inv));
        when(orgRepo.findByIdForUpdate(org.getId())).thenReturn(Optional.of(org));

        assertThatThrownBy(() -> service.acceptInvite(userId, new AcceptInviteRequest("t3")))
                .isInstanceOf(InviteConflictException.class);
    }
}
