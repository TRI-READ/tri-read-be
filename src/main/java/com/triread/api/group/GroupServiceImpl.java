package com.triread.api.group;

import com.triread.api.common.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.time.temporal.ChronoUnit;
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

        InviteCodeResponse invite = createInvite(group.getId(), userId, 7, 20, false);
        return new CreatedGroupResponse(getGroup(group.getId(), userId), invite.inviteCode());
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
    public InviteCodeResponse renewInvite(long groupId, long userId, Integer expiresInDays,
                                          Integer maxUses, boolean revokeExisting) {
        requireOwnerGroup(groupId, userId);
        return createInvite(groupId, userId, expiresInDays, maxUses, revokeExisting);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InviteSummary> getInvites(long groupId, long userId) {
        requireOwnerGroup(groupId, userId);
        return groupMapper.findInvites(groupId).stream().map(InviteSummary::from).toList();
    }

    @Override
    @Transactional
    public void revokeInvite(long groupId, long inviteId, long userId) {
        requireOwnerGroup(groupId, userId);
        if (groupMapper.disableInvite(groupId, inviteId) != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, "GROUP_INVITE_NOT_FOUND",
                    "The active invite was not found.");
        }
    }

    @Override
    @Transactional
    public void removeMember(long groupId, long memberUserId, long userId) {
        requireOwnerGroup(groupId, userId);
        if (memberUserId == userId) {
            throw new ApiException(HttpStatus.CONFLICT, "OWNER_CANNOT_BE_REMOVED",
                    "Transfer ownership before leaving the group.");
        }
        GroupData.MemberRow member = groupMapper.findMember(groupId, memberUserId);
        if (member == null || !MEMBER_ROLE.equals(member.role())
                || groupMapper.deleteMember(groupId, memberUserId) != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, "GROUP_MEMBER_NOT_FOUND",
                    "The group member was not found.");
        }
    }

    @Override
    @Transactional
    public GroupDetail transferOwnership(long groupId, long newOwnerUserId, long userId) {
        requireOwnerGroup(groupId, userId);
        if (newOwnerUserId == userId) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_GROUP_OWNER",
                    "This user already owns the group.");
        }
        GroupData.MemberRow newOwner = groupMapper.findMember(groupId, newOwnerUserId);
        if (newOwner == null || !MEMBER_ROLE.equals(newOwner.role())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "GROUP_MEMBER_NOT_FOUND",
                    "The group member was not found.");
        }
        if (groupMapper.updateMemberRole(groupId, userId, MEMBER_ROLE) != 1
                || groupMapper.updateMemberRole(groupId, newOwnerUserId, OWNER_ROLE) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "GROUP_OWNER_TRANSFER_FAILED",
                    "Group ownership could not be transferred.");
        }
        return getGroup(groupId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public GroupActivity getWeeklyActivity(long groupId, long userId) {
        GroupData.GroupRow group = requireMemberGroup(groupId, userId);
        LocalDate today = LocalDate.now(clock);
        LocalDate startDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate endDate = startDate.plusDays(4);
        List<GroupData.ActivityRow> rows = groupMapper.findWeeklyActivity(
                groupId, startDate, endDate.plusDays(2), today
        );
        List<MemberActivity> ranking = new ArrayList<>();
        int rank = 0;
        int previousScore = Integer.MIN_VALUE;
        for (int index = 0; index < rows.size(); index++) {
            GroupData.ActivityRow row = rows.get(index);
            if (row.activityScore() != previousScore) {
                rank = index + 1;
                previousScore = row.activityScore();
            }
            ranking.add(MemberActivity.from(rank, row));
        }
        int todayCompletedCount = (int) rows.stream()
                .filter(GroupData.ActivityRow::todayCompleted)
                .count();
        return new GroupActivity(startDate, endDate, group.memberCount(),
                todayCompletedCount, ranking);
    }

    private InviteCodeResponse createInvite(long groupId, long userId, Integer expiresInDays,
                                            Integer maxUses, boolean revokeExisting) {
        if (expiresInDays != null && (expiresInDays < 1 || expiresInDays > 30)
                || maxUses != null && (maxUses < 1 || maxUses > 100)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INVITE_POLICY",
                    "Invite expiry or usage limit is outside the allowed range.");
        }
        if (revokeExisting) {
            groupMapper.disableGroupInvites(groupId);
        }
        String inviteCode = inviteCodeService.generateCode();
        String normalizedCode = inviteCodeService.normalize(inviteCode);
        Instant expiresAt = expiresInDays == null ? null
                : clock.instant().plus(expiresInDays, ChronoUnit.DAYS);
        groupMapper.insertInvite(groupId, inviteCodeService.hash(normalizedCode), userId,
                expiresAt, maxUses);
        GroupData.InviteManagementRow created = groupMapper.findInvites(groupId).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Created invite could not be loaded"));
        return new InviteCodeResponse(inviteCode, InviteSummary.from(created));
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
