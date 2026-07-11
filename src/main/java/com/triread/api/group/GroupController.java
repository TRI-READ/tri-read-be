package com.triread.api.group;

import com.triread.api.auth.AuthPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
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
            @Positive @PathVariable long groupId
    ) {
        GroupService.InviteCodeResponse result = groupService.renewInvite(
                groupId,
                principal.userId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
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
}
