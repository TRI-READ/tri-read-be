package com.triread.api.group;

import com.triread.api.common.ApiException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
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

        InviteCodeResponse invite = createInvite(group.getId(), userId, 7, 20, false);
        return new CreatedGroupResponse(getGroup(group.getId(), userId), invite.inviteCode());
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupSummary> getMyGroups(long userId) {
        List<GroupSummary> groups = new ArrayList<>();
        for (GroupData.GroupRow row : groupMapper.findMyGroups(userId)) {
            groups.add(GroupSummary.from(row));
        }
        return groups;
    }

    @Override
    @Transactional(readOnly = true)
    public GroupDetail getGroup(long groupId, long userId) {
        GroupData.GroupRow group = requireMemberGroup(groupId, userId);
        List<GroupMember> members = new ArrayList<>();
        for (GroupData.MemberRow row : groupMapper.findMembers(groupId)) {
            members.add(GroupMember.from(row));
        }
        return GroupDetail.from(group, members);
    }

    @Override
    @Transactional
    public GroupDetail joinGroup(long userId, String rawInviteCode) {
        GroupData.InviteRow invite = findAvailableInvite(rawInviteCode);
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
        List<InviteSummary> invites = new ArrayList<>();
        for (GroupData.InviteManagementRow row : groupMapper.findInvites(groupId)) {
            invites.add(InviteSummary.from(row));
        }
        return invites;
    }

    @Override
    @Transactional
    public void revokeInvite(long groupId, long inviteId, long userId) {
        requireOwnerGroup(groupId, userId);
        if (groupMapper.disableInvite(groupId, inviteId) != 1) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "GROUP_INVITE_NOT_FOUND",
                    "The active invite was not found."
            );
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
        requireRegularMember(member);
        if (groupMapper.deleteMember(groupId, memberUserId) != 1) {
            throw memberNotFoundException();
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
        requireRegularMember(newOwner);
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
        List<MemberActivity> ranking = createRanking(rows);
        int todayCompletedCount = countTodayCompleted(rows);
        return new GroupActivity(startDate, endDate, group.memberCount(),
                todayCompletedCount, ranking);
    }

    private List<MemberActivity> createRanking(List<GroupData.ActivityRow> rows) {
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
        return ranking;
    }

    private int countTodayCompleted(List<GroupData.ActivityRow> rows) {
        int completedCount = 0;
        for (GroupData.ActivityRow row : rows) {
            if (row.todayCompleted()) {
                completedCount++;
            }
        }
        return completedCount;
    }

    private InviteCodeResponse createInvite(long groupId, long userId, Integer expiresInDays,
                                            Integer maxUses, boolean revokeExisting) {
        validateInvitePolicy(expiresInDays, maxUses);
        if (revokeExisting) {
            groupMapper.disableGroupInvites(groupId);
        }

        String inviteCode = inviteCodeService.generateCode();
        String normalizedCode = inviteCodeService.normalize(inviteCode);
        Instant expiresAt = calculateExpiration(expiresInDays);
        groupMapper.insertInvite(groupId, inviteCodeService.hash(normalizedCode), userId,
                expiresAt, maxUses);
        GroupData.InviteManagementRow created = findNewestInvite(groupId);
        return new InviteCodeResponse(inviteCode, InviteSummary.from(created));
    }

    private GroupData.InviteRow findAvailableInvite(String rawInviteCode) {
        String normalizedCode = inviteCodeService.normalize(rawInviteCode);
        if (normalizedCode.length() != GroupInviteCodeService.CODE_LENGTH) {
            throw invalidInviteException();
        }

        String inviteHash = inviteCodeService.hash(normalizedCode);
        GroupData.InviteRow invite = groupMapper.findInviteForUpdate(inviteHash);
        if (invite == null || isUnavailable(invite)) {
            throw invalidInviteException();
        }
        return invite;
    }

    private void validateInvitePolicy(Integer expiresInDays, Integer maxUses) {
        boolean invalidExpiration = expiresInDays != null
                && (expiresInDays < 1 || expiresInDays > 30);
        boolean invalidMaxUses = maxUses != null && (maxUses < 1 || maxUses > 100);
        if (invalidExpiration || invalidMaxUses) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INVITE_POLICY",
                    "Invite expiry or usage limit is outside the allowed range.");
        }
    }

    private Instant calculateExpiration(Integer expiresInDays) {
        if (expiresInDays == null) {
            return null;
        }
        return clock.instant().plus(expiresInDays, ChronoUnit.DAYS);
    }

    private GroupData.InviteManagementRow findNewestInvite(long groupId) {
        List<GroupData.InviteManagementRow> invites = groupMapper.findInvites(groupId);
        if (invites.isEmpty()) {
            throw new IllegalStateException("Created invite could not be loaded");
        }
        return invites.get(0);
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

    private void requireRegularMember(GroupData.MemberRow member) {
        if (member == null || !MEMBER_ROLE.equals(member.role())) {
            throw memberNotFoundException();
        }
    }

    private boolean isUnavailable(GroupData.InviteRow invite) {
        Instant now = clock.instant();
        boolean expired = invite.expiresAt() != null && !invite.expiresAt().isAfter(now);
        boolean fullyUsed = invite.maxUses() != null
                && invite.usedCount() >= invite.maxUses();
        return expired || fullyUsed;
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

    private ApiException memberNotFoundException() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "GROUP_MEMBER_NOT_FOUND",
                "The group member was not found."
        );
    }
}
