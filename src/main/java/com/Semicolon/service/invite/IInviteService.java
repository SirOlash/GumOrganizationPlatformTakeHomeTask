package com.Semicolon.service.invite;

import com.Semicolon.dtos.request.AcceptInviteRequest;
import com.Semicolon.dtos.request.CreateInviteRequest;
import com.Semicolon.dtos.response.AcceptInviteResponse;
import com.Semicolon.dtos.response.InviteListItem;
import com.Semicolon.dtos.response.InviteResponse;

import java.util.List;
import java.util.UUID;

public interface IInviteService {
    InviteResponse createInvite(UUID adminUserId, CreateInviteRequest request);
    List<InviteListItem> getInvitesForUser(UUID userId);
    AcceptInviteResponse acceptInvite(UUID userId, AcceptInviteRequest request);
}
