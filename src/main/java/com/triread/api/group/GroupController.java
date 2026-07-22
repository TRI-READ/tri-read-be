package com.triread.api.group;

import com.triread.api.auth.AuthPrincipal;
import com.triread.api.audit.AdminAuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;
    private final AdminAuditService auditService;

    public GroupController(GroupService groupService, AdminAuditService auditService) {
        this.groupService = groupService;
        this.auditService = auditService;
    }

    @PostMapping
    public ResponseEntity<GroupService.CreatedGroupResponse> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateGroupRequest request
    ) {
        GroupService.CreatedGroupResponse result = groupService.createGroup(
                principal.userId(),
                request.name(),
                request.description()
        );
        auditService.record(principal.userId(), "GROUP_CREATED", "GROUP",
                result.group().groupId(), Map.of("name", result.group().name()));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/my")
    public List<GroupService.GroupSummary> myGroups(
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return groupService.getMyGroups(principal.userId());
    }

    @GetMapping("/{groupId}")
    public GroupService.GroupDetail detail(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Positive @PathVariable long groupId
    ) {
        return groupService.getGroup(groupId, principal.userId());
    }

    @GetMapping("/{groupId}/activity")
    public GroupService.GroupActivity activity(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Positive @PathVariable long groupId
    ) {
        return groupService.getWeeklyActivity(groupId, principal.userId());
    }

    @PostMapping("/join")
    public ResponseEntity<GroupService.GroupDetail> join(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody JoinGroupRequest request
    ) {
        GroupService.GroupDetail result = groupService.joinGroup(
                principal.userId(),
                request.inviteCode()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/{groupId}/invites")
    public ResponseEntity<GroupService.InviteCodeResponse> renewInvite(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Positive @PathVariable long groupId,
            @Valid @RequestBody(required = false) CreateInviteRequest request
    ) {
        CreateInviteRequest policy = request == null
                ? new CreateInviteRequest(7, 20, true)
                : request;
        GroupService.InviteCodeResponse result = groupService.renewInvite(
                groupId,
                principal.userId(),
                policy.expiresInDays(),
                policy.maxUses(),
                policy.revokeExisting()
        );
        auditService.record(principal.userId(), "GROUP_INVITE_CREATED", "GROUP", groupId,
                Map.of("inviteId", result.invite().inviteId(),
                        "expiresInDays", policy.expiresInDays(),
                        "maxUses", policy.maxUses(),
                        "revokeExisting", policy.revokeExisting()));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/{groupId}/invites")
    public List<GroupService.InviteSummary> invites(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Positive @PathVariable long groupId
    ) {
        return groupService.getInvites(groupId, principal.userId());
    }

    @DeleteMapping("/{groupId}/invites/{inviteId}")
    public ResponseEntity<Void> revokeInvite(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Positive @PathVariable long groupId,
            @Positive @PathVariable long inviteId
    ) {
        groupService.revokeInvite(groupId, inviteId, principal.userId());
        auditService.record(principal.userId(), "GROUP_INVITE_REVOKED", "GROUP", groupId,
                Map.of("inviteId", inviteId));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{groupId}/members/{memberUserId}")
    public ResponseEntity<Void> removeMember(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Positive @PathVariable long groupId,
            @Positive @PathVariable long memberUserId
    ) {
        groupService.removeMember(groupId, memberUserId, principal.userId());
        auditService.record(principal.userId(), "GROUP_MEMBER_REMOVED", "GROUP", groupId,
                Map.of("memberUserId", memberUserId));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{groupId}/owner")
    public GroupService.GroupDetail transferOwnership(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Positive @PathVariable long groupId,
            @Valid @RequestBody TransferOwnershipRequest request
    ) {
        GroupService.GroupDetail result = groupService.transferOwnership(
                groupId, request.newOwnerUserId(), principal.userId());
        auditService.record(principal.userId(), "GROUP_OWNER_TRANSFERRED", "GROUP", groupId,
                Map.of("newOwnerUserId", request.newOwnerUserId()));
        return result;
    }

    public record CreateGroupRequest(
            @NotBlank
            @Size(max = 100)
            String name,

            @Size(max = 500)
            String description
    ) {
    }

    public record JoinGroupRequest(
            @NotBlank
            @Size(max = 20)
            @Pattern(
                    regexp = "[A-Za-z0-9 -]+",
                    message = "Invite code contains invalid characters."
            )
            String inviteCode
    ) {
    }

    public record CreateInviteRequest(
            @Min(1) @Max(30) int expiresInDays,
            @Min(1) @Max(100) int maxUses,
            boolean revokeExisting
    ) {
    }

    public record TransferOwnershipRequest(@Positive long newOwnerUserId) {
    }
}
