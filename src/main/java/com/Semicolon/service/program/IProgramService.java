package com.Semicolon.service.program;

import com.Semicolon.dtos.request.ArchiveProgramRequest;
import com.Semicolon.dtos.request.CreateProgramRequest;
import com.Semicolon.dtos.request.FindProgramRequest;
import com.Semicolon.dtos.request.UpdateProgramRequest;
import com.Semicolon.dtos.response.ProgramResponse;
import com.Semicolon.dtos.response.ProgramsListResponse;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

public interface IProgramService {
    ProgramResponse createProgram(UUID userId, CreateProgramRequest request);
    ProgramResponse updateProgram(UUID userId, UUID programId, UpdateProgramRequest request);
    ProgramResponse getProgram(UUID userId, FindProgramRequest request);
    List<ProgramsListResponse> listPrograms(UUID userId);
//    Page<ProgramResponse> listPrograms(UUID userId, UUID organizationId, String q, String status, int page, int size);
    ProgramResponse archiveProgram(UUID userId, ArchiveProgramRequest request);
}
