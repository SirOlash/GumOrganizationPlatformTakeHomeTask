package com.Semicolon.controllers;

import com.Semicolon.dtos.request.AcceptInviteRequest;
import com.Semicolon.dtos.request.CreateInviteRequest;
import com.Semicolon.dtos.response.AcceptInviteResponse;
import com.Semicolon.dtos.response.InviteListItem;
import com.Semicolon.dtos.response.InviteResponse;
import com.Semicolon.service.invite.IInviteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/v1/invites", produces = MediaType.APPLICATION_JSON_VALUE)
public class InviteController {
    private final IInviteService inviteService;

    public InviteController(IInviteService inviteService){
        this.inviteService = inviteService;
    }

    @PostMapping("/admin/invite-member")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InviteResponse> createInvite(@AuthenticationPrincipal UUID userId, @Valid @RequestBody CreateInviteRequest request) {
        if (userId == null) return ResponseEntity.status(401).build();
        InviteResponse response = inviteService.createInvite(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/view-invites")
    public ResponseEntity<List<InviteListItem>> getInvitesForUser(@AuthenticationPrincipal UUID userId){
        if (userId == null) return ResponseEntity.status(401).build();
        List<InviteListItem> invite = inviteService.getInvitesForUser(userId);
        return ResponseEntity.ok(invite);
    }

    @PostMapping("/accept-invite")
    public ResponseEntity<AcceptInviteResponse> acceptInvite(@AuthenticationPrincipal UUID userId, @Valid @RequestBody AcceptInviteRequest request){
        if (userId == null) return ResponseEntity.status(401).build();
        AcceptInviteResponse response = inviteService.acceptInvite(userId, request);
        return ResponseEntity.ok(response);
    }



}
