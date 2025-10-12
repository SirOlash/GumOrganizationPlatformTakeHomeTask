package com.Semicolon.data.models;


import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@RequiredArgsConstructor
@Data
@Entity
@Table(name = "organizations", indexes = @Index(columnList = "name", unique = true))
public class Organization {
    @Id
    @GeneratedValue
    private UUID id;

    @NonNull
    @Column(nullable = false, unique = true)
    private String name;

    @OneToMany(mappedBy = "organization")
    private List<User> users = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Plan plan = Plan.FREE;

    private String logoUrl;
    private String website;
    private String industry;
    @Column(length = 2000)
    private String description;

    @Column(name = "members_count", nullable = false)
    private int membersCount = 0;

    @Column(name = "programs_count", nullable = false)
    private int programsCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;


    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

}
