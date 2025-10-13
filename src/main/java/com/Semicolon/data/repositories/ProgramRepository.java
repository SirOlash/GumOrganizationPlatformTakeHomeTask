package com.Semicolon.data.repositories;

import com.Semicolon.data.models.Organization;
import com.Semicolon.data.models.Program;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProgramRepository extends JpaRepository <Program, UUID>{
    List<Program> findByOrganization_Id(UUID organizationId);

    Optional<Program> findByIdAndOrganization_Id(UUID id, UUID organizationId);
    Optional<Program> findByNameIgnoreCaseAndOrganization_Id(String name, UUID organizationId);

    boolean existsByOrganizationAndNameIgnoreCase(Organization organization, String name);
}
