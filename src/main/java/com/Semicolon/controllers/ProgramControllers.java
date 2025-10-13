package com.Semicolon.controllers;


import com.Semicolon.dtos.request.ArchiveProgramRequest;
import com.Semicolon.dtos.request.CreateProgramRequest;
import com.Semicolon.dtos.request.FindProgramRequest;
import com.Semicolon.dtos.request.UpdateProgramRequest;
import com.Semicolon.dtos.response.ProgramResponse;
import com.Semicolon.dtos.response.ProgramsListResponse;
import com.Semicolon.service.program.IProgramService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/programs")
public class ProgramControllers {
    private static final Logger log = LoggerFactory.getLogger(ProgramControllers.class);
    private final IProgramService programService;

    public ProgramControllers(IProgramService programService) {
        this.programService = programService;
    }

    @PostMapping
    public ResponseEntity<ProgramResponse> createProgram(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody CreateProgramRequest request) {

        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        log.debug("createProgram called by user={} req={}", userId, request);
        ProgramResponse resp = programService.createProgram(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @PatchMapping("/{programId}")
    public ResponseEntity<ProgramResponse> updateProgram(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID programId,
            @Valid @RequestBody UpdateProgramRequest request) {

        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        log.debug("updateProgram called by user={} programId={} req={}", userId, programId, request);
        ProgramResponse resp = programService.updateProgram(userId, programId, request);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/show-program")
    public ResponseEntity<ProgramResponse> showProgram(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody FindProgramRequest request) {

        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        log.debug("getProgramByName called by user={} name={}", userId, request.getProgramName());
        ProgramResponse resp = programService.getProgram(userId, request);
        return ResponseEntity.ok(resp);
    }

    @GetMapping
    public ResponseEntity<List<ProgramsListResponse>> listPrograms(
            @AuthenticationPrincipal UUID userId) {

        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        log.debug("listPrograms called by user={}", userId);
        List<ProgramsListResponse> list = programService.listPrograms(userId);
        return ResponseEntity.ok(list);
    }

    @PostMapping("/archive-program")
    public ResponseEntity<ProgramResponse> archiveProgram(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody ArchiveProgramRequest request) {

        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        log.debug("archiveProgram called by user={} programName={}", userId, request.getProgramName());
        ProgramResponse resp = programService.archiveProgram(userId, request);
        return ResponseEntity.ok(resp);
    }
}
