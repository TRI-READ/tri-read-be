package com.triread.api.group;

import com.triread.api.common.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupServiceImpl implements GroupService {

    private static final String OWNER_ROLE = "OWNER";
    private static final String MEMBER_ROLE = "MEMBER";

    private final GroupMapper groupMapper;
    private final GroupInviteCodeService inviteCodeService;
    private final Clock clock;

    public GroupServiceImpl(
            GroupMapper groupMapper,
            GroupInviteCodeService inviteCodeService,
            Clock clock
    ) {
        this.groupMapper = groupMapper;
        this.inviteCodeService = inviteCodeService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CreatedGroupResponse createGroup(long userId, String name, String description) {
        GroupData.GroupInsert group = new GroupData.GroupInsert(
                name.trim(),
                normalizeDescription(description),
                userId
        );
        groupMapper.insertGroup(group);
        groupMapper.insertMember(group.getId(), userId, OWNER_ROLE);

        String inviteCode = createInvite(group.getId(), userId, false);
        return new CreatedGroupResponse(getGroup(group.getId(), userId), inviteCode);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupSummary> getMyGroups(long userId) {
        return groupMapper.findMyGroups(userId).stream()
                .map(GroupSummary::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public GroupDetail getGroup(long groupId, long userId) {
        GroupData.GroupRow group = requireMemberGroup(groupId, userId);
        List<GroupMember> members = groupMapper.findMembers(groupId).stream()
                .map(GroupMember::from)
                .toList();
        return GroupDetail.from(group, members);
    }

    @Override
    @Transactional
    public GroupDetail joinGroup(long userId, String rawInviteCode) {
        String normalizedCode = inviteCodeService.normalize(rawInviteCode);
        if (normalizedCode.length() != GroupInviteCodeService.CODE_LENGTH) {
            throw invalidInviteException();
        }

        GroupData.InviteRow invite = groupMapper.findInviteForUpdate(
                inviteCodeService.hash(normalizedCode)
        );
        if (invite == null || isUnavailable(invite)) {
            throw invalidInviteException();
        }
        if (groupMapper.findGroupForMember(invite.groupId(), userId) != null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "GROUP_ALREADY_JOINED",
                    "You are already a member of this group."
            );
        }

        groupMapper.insertMember(invite.groupId(), userId, MEMBER_ROLE);
        groupMapper.consumeInvite(invite.inviteId());
        return getGroup(invite.groupId(), userId);
    }

    @Override
    @Transactional
    public InviteCodeResponse renewInvite(long groupId, long userId) {
        requireOwnerGroup(groupId, userId);
        return new InviteCodeResponse(createInvite(groupId, userId, true));
    }

    private String createInvite(long groupId, long userId, boolean rotateExisting) {
        if (rotateExisting) {
            groupMapper.disableGroupInvites(groupId);
        }
        String inviteCode = inviteCodeService.generateCode();
        String normalizedCode = inviteCodeService.normalize(inviteCode);
        groupMapper.insertInvite(groupId, inviteCodeService.hash(normalizedCode), userId);
        return inviteCode;
    }

    private GroupData.GroupRow requireMemberGroup(long groupId, long userId) {
        GroupData.GroupRow group = groupMapper.findGroupForMember(groupId, userId);
        if (group == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "GROUP_NOT_FOUND",
                    "The group was not found."
            );
        }
        return group;
    }

    private void requireOwnerGroup(long groupId, long userId) {
        GroupData.GroupRow group = requireMemberGroup(groupId, userId);
        if (!OWNER_ROLE.equals(group.role())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "GROUP_OWNER_REQUIRED",
                    "Only the group owner can perform this action."
            );
        }
    }

    private boolean isUnavailable(GroupData.InviteRow invite) {
        Instant now = clock.instant();
        return invite.expiresAt() != null && !invite.expiresAt().isAfter(now)
                || invite.maxUses() != null && invite.usedCount() >= invite.maxUses();
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }

    private ApiException invalidInviteException() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_INVITE_CODE",
                "The invite code is invalid or no longer available."
        );
    }
}
