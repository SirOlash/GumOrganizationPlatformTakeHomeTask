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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class InviteServiceImpl implements IInviteService{

    private final InviteRepository inviteRepo;
    private final UserRepository userRepo;
    private final OrganizationRepository orgRepo;
    private final OutboxEmailRepository outboxRepo;
    private final OutboxWorker outboxWorker;

    public InviteServiceImpl(InviteRepository inviteRepo,
                             UserRepository userRepo,
                             OrganizationRepository orgRepo,
                             OutboxEmailRepository outboxRepo,
                             OutboxWorker outboxWorker) {
        this.inviteRepo = inviteRepo;
        this.userRepo = userRepo;
        this.orgRepo = orgRepo;
        this.outboxRepo = outboxRepo;
        this.outboxWorker = outboxWorker;
    }

    private static final int DEFAULT_EXPIRES_DAYS = 7;

    @Override
    @Transactional
    public InviteResponse createInvite(UUID adminUserId, CreateInviteRequest request) {
        System.out.println("[TEST DEBUG] createInvite called with adminId=" + adminUserId + " req=" + request);

        User admin = userRepo.findById(adminUserId).orElseThrow(() -> new RuntimeException("Admin user not found"));

        if (admin.getRole() != Role.ADMIN) {
            throw new InviteForbiddenException("only organization admins can create invites");
        }
        Organization org = admin.getOrganization();
        if (org == null) throw new OrganizationNotFoundException("Admin has no organization");

        String email = request.getEmail().trim().toLowerCase();

        userRepo.findByEmail(email).ifPresent(u -> {
            if (u.getOrganization() != null) {
                throw new InviteConflictException("user already belongs to an organization");
            }
        });
        int limit = planLimit(org.getPlan());
        if (org.getMembersCount() >= limit) {
            throw new LimitExceededException("Plan limit exceeded", "members", "limit");
        }

        List<InviteStatus> active = List.of(InviteStatus.ISSUED, InviteStatus.QUEUED, InviteStatus.SENT);
        boolean exists = inviteRepo.existsByEmailIgnoreCaseAndOrganization_IdAndStatusIn(email, org.getId(), active);
        if (exists) throw new InviteConflictException("active invite already exists for this email");

        if (request.getRole() == Role.ADMIN) {
            throw new InviteForbiddenException("You can't invite another admin");
        }

        String token = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Instant expiresAt = Instant.now().plus(DEFAULT_EXPIRES_DAYS, ChronoUnit.DAYS);

        Invite invite = new Invite();
        invite.setToken(token);
        invite.setEmail(email);
        invite.setRole(request.getRole());
        invite.setOrganization(org);
        invite.setStatus(InviteStatus.ISSUED);
        invite.setExpiresAt(expiresAt);
        inviteRepo.save(invite);

        OutboxEmail outbox = new OutboxEmail();
        outbox.setVerificationToken(token);
        outbox.setToEmail(email);
        outbox.setSubject("Invite to join " + org.getName());
        outbox.setBody("Invite token: " + token);
        outbox.setAttempts(0);
        outbox.setMaxAttempts(3);
        outbox.setStatus(OutboxStatus.QUEUED);
        outbox.setForceFail(false);
        outbox.setCreatedAt(Instant.now());
        outboxRepo.save(outbox);

        OutboxStatus sendStatus = outboxWorker.processOutboxAttempt(outbox.getId());
        if (sendStatus == OutboxStatus.QUEUED) {
            invite.setStatus(InviteStatus.QUEUED);
            inviteRepo.save(invite);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    outboxWorker.triggerRetriesForToken(outbox.getVerificationToken());
                }
            });
        } else if (sendStatus == OutboxStatus.SENT) {
            invite.setStatus(InviteStatus.SENT);
            inviteRepo.save(invite);
        } else {
            invite.setStatus(InviteStatus.QUEUED);
            inviteRepo.save(invite);
        }

        InviteResponse resp = new InviteResponse();
        resp.setEmail(invite.getEmail());
        resp.setRole(invite.getRole());
        resp.setToken(invite.getToken());
        resp.setCreatedAt(invite.getCreatedAt());
        resp.setExpiresAt(invite.getExpiresAt());
        return resp;
    }

    @Override
    @Transactional(readOnly = true)
    public List<InviteListItem> getInvitesForUser(UUID userId) {
        User user = userRepo.findById(userId).orElseThrow(() -> new UserNotFoundException("User not found"));
        String email = user.getEmail().trim().toLowerCase();
        List<InviteStatus> active = List.of(InviteStatus.ISSUED, InviteStatus.QUEUED, InviteStatus.SENT);
        List<Invite> invites = inviteRepo.findByEmailIgnoreCaseAndStatusIn(email, active);
        List<InviteListItem> result = new ArrayList<>(invites.size());
        for (Invite inv : invites) {
            UUID orgId = inv.getOrganization() == null ? null : inv.getOrganization().getId();
            result.add(new InviteListItem(
                    inv.getId(),
                    inv.getToken(),
                    inv.getRole(),
                    inv.getStatus(),
                    inv.getCreatedAt(),
                    inv.getExpiresAt(),
                    orgId
            ));
        }
        return result;

    }

    @Override
    @Transactional
    public AcceptInviteResponse acceptInvite(UUID userId, AcceptInviteRequest request) {
        User user = userRepo.findById(userId).orElseThrow(() -> new UserNotFoundException("User not found"));

        String token = request.getToken().trim();
        Invite invite = inviteRepo.findByToken(token).orElseThrow(() -> new InviteNotFoundException("Invite token invalid"));

        if (invite.getStatus() == InviteStatus.USED) {
            throw new InviteConflictException("TOKEN_ALREADY_USED");
        }
        if (invite.getExpiresAt() != null && Instant.now().isAfter(invite.getExpiresAt())) {
            invite.setStatus(InviteStatus.EXPIRED);
            inviteRepo.save(invite);
            throw new InviteNotFoundException("TOKEN_EXPIRED");
        }

        if (!invite.getEmail().equalsIgnoreCase(user.getEmail())) {
            throw new InviteForbiddenException("token does not belong to this user");
        }

        UUID orgId = invite.getOrganization() == null ? null : invite.getOrganization().getId();
        if (orgId == null) throw new OrganizationNotFoundException("Invite missing organization");

        Organization org = orgRepo.findByIdForUpdate(orgId).orElseThrow(() -> new RuntimeException("Organization not found"));
        int limit = planLimit(org.getPlan());
        if (org.getMembersCount() >= limit) {
            throw new LimitExceededException("organization has reached its plan members limit");
        }
        if (user.getOrganization() != null) {
            if (user.getOrganization().getId().equals(org.getId())) {
                throw new InviteConflictException("user already a member of this organization");
            } else {
                throw new InviteConflictException("user belongs to another organization");
            }
        }
        invite.setStatus(InviteStatus.USED);
        inviteRepo.save(invite);

        user.setOrganization(org);
        user.setRole(invite.getRole());
        userRepo.save(user);

        org.setMembersCount(org.getMembersCount() + 1);
        orgRepo.save(org);

        return new AcceptInviteResponse("INVITE_ACCEPTED", "Invite accepted and membership created");
    }

    public int planLimit(Plan plan) {
        if (plan == null) return Integer.MAX_VALUE;
        switch (plan) {
            case FREE: return 5;
            case PRO: return 50;
            case ENTERPRISE: default: return Integer.MAX_VALUE;
        }
    }
}
