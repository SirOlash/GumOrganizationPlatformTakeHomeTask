package com.Semicolon.service.auth;

import com.Semicolon.data.models.*;
import com.Semicolon.data.repositories.OrganizationRepository;
import com.Semicolon.data.repositories.OutboxEmailRepository;
import com.Semicolon.data.repositories.UserRepository;
import com.Semicolon.data.repositories.VerificationTokenRepository;
import com.Semicolon.dtos.request.AdminRegistrationRequest;
import com.Semicolon.dtos.response.AdminRegistrationResponse;
import com.Semicolon.exceptions.api.EmailInUseException;
import com.Semicolon.exceptions.api.OrganizationInUseException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class RegistrationServiceImpl implements IRegistrationService{
    private final UserRepository userRepository;
    private final VerificationTokenRepository tokenRepo;
    private final OrganizationRepository orgRepo;
    private final OutboxEmailRepository outboxRepo;
    private final PasswordEncoder passwordEncoder;

    public RegistrationServiceImpl(UserRepository userRepo,
                                   VerificationTokenRepository tokenRepo,
                                   OrganizationRepository orgRepo,
                                   PasswordEncoder passwordEncoder,
                                   OutboxEmailRepository outboxRepo) {
        this.userRepository = userRepo;
        this.tokenRepo = tokenRepo;
        this.orgRepo = orgRepo;
        this.outboxRepo = outboxRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    @Override
    public AdminRegistrationResponse register(AdminRegistrationRequest adminRequest) {
        String email = adminRequest.getEmail().trim().toLowerCase();

        Optional<User> existingUser = userRepository.findByEmail(email);
        if (existingUser.isPresent()) {
            User existing = existingUser.get();

            if (existing.getStatus() == UserStatus.VERIFIED) {
                throw new EmailInUseException();
            }

            String requestedOrgName = StringUtils.hasText(adminRequest.getOrganizationName())
                    ? adminRequest.getOrganizationName().trim()
                    : null;

            if (existing.getOrganization() == null && requestedOrgName == null) {
                String token = createAndSaveVerificationToken(existing);
                return new AdminRegistrationResponse("VERIFICATION_RESENT", token);
            }

            if (existing.getOrganization() != null && existing.getOrganization().getName().equals(requestedOrgName)) {
                String token = createAndSaveVerificationToken(existing);
                return new AdminRegistrationResponse("VERIFICATION_RESENT", token);
            }

            throw new EmailInUseException("Email already used to register for an organization");
        }

        Organization org = null;
        if (StringUtils.hasText(adminRequest.getOrganizationName())) {
            String orgName = adminRequest.getOrganizationName().trim();

            if (orgRepo.existsByName(orgName)) {
                throw new OrganizationInUseException();
            }

            try {
                org = new Organization(orgName);
                org.setMembersCount(1);
                orgRepo.save(org);
            } catch (DataIntegrityViolationException dive) {
                throw new OrganizationInUseException();
            }
        }


        String hashed = passwordEncoder.encode(adminRequest.getPassword());
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(hashed);
        user.setStatus(UserStatus.PENDING);

        user.setRole(org != null ? Role.ADMIN : Role.MEMBER);
        user.setOrganization(org);
        userRepository.save(user);

        String token = createAndSaveVerificationToken(user);

        return new AdminRegistrationResponse("Registration successful. Please verify your email.", token);
    }

    /* ---------- helpers ---------- */

    private String createAndSaveVerificationToken(User user) {
        String token = UUID.randomUUID().toString();
        Instant expires = Instant.now().plus(24, ChronoUnit.HOURS);
        VerificationToken vt = new VerificationToken(token, user, expires);
        tokenRepo.save(vt);
        return token;
    }
}
