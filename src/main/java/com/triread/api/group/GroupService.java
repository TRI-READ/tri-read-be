package com.triread.api.group;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface GroupService {

    CreatedGroupResponse createGroup(long userId, String name, String description);

    List<GroupSummary> getMyGroups(long userId);

    GroupDetail getGroup(long groupId, long userId);

    GroupDetail joinGroup(long userId, String rawInviteCode);

    InviteCodeResponse renewInvite(long groupId, long userId);

    GroupActivity getWeeklyActivity(long groupId, long userId);

    record CreatedGroupResponse(GroupDetail group, String inviteCode) {
    }

    record InviteCodeResponse(String inviteCode) {
    }

    record GroupSummary(
            long groupId,
            String name,
            String description,
            String role,
            int memberCount,
            Instant createdAt
    ) {
        static GroupSummary from(GroupData.GroupRow group) {
            return new GroupSummary(
                    group.groupId(),
                    group.name(),
                    group.description(),
                    group.role(),
                    group.memberCount(),
                    group.createdAt()
            );
        }
    }

    record GroupDetail(
            long groupId,
            String name,
            String description,
            String role,
            int memberCount,
            Instant createdAt,
            List<GroupMember> members
    ) {
        static GroupDetail from(GroupData.GroupRow group, List<GroupMember> members) {
            return new GroupDetail(
                    group.groupId(),
                    group.name(),
                    group.description(),
                    group.role(),
                    group.memberCount(),
                    group.createdAt(),
                    members
            );
        }
    }

    record GroupMember(
            long userId,
            String displayName,
            String role,
            Instant joinedAt
    ) {
        static GroupMember from(GroupData.MemberRow member) {
            return new GroupMember(
                    member.userId(),
                    member.displayName(),
                    member.role(),
                    member.joinedAt()
            );
        }
    }

    record GroupActivity(
            LocalDate startDate,
            LocalDate endDate,
            int memberCount,
            int todayCompletedCount,
            List<MemberActivity> ranking
    ) {
    }

    record MemberActivity(
            int rank,
            long userId,
            String displayName,
            String role,
            int completedDays,
            int averageScore,
            int perfectCount,
            int recoveredCount,
            int fullyLitCount,
            boolean todayCompleted,
            int activityScore
    ) {
        static MemberActivity from(int rank, GroupData.ActivityRow row) {
            int averageScore = row.completedDays() == 0
                    ? 0
                    : Math.round((float) row.totalCorrect() / row.completedDays());
            return new MemberActivity(rank, row.userId(), row.displayName(), row.role(),
                    row.completedDays(), averageScore, row.perfectCount(), row.recoveredCount(),
                    row.fullyLitCount(), row.todayCompleted(), row.activityScore());
        }
    }
}
