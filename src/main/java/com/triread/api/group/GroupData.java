package com.triread.api.group;

import java.time.Instant;

public final class GroupData {

    private GroupData() {
    }

    public static final class GroupInsert {

        private Long id;
        private final String name;
        private final String description;
        private final long createdByUserId;

        public GroupInsert(String name, String description, long createdByUserId) {
            this.name = name;
            this.description = description;
            this.createdByUserId = createdByUserId;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public long getCreatedByUserId() {
            return createdByUserId;
        }
    }

    public record GroupRow(
            long groupId,
            String name,
            String description,
            String role,
            int memberCount,
            Instant createdAt
    ) {
    }

    public record MemberRow(
            long userId,
            String displayName,
            String role,
            Instant joinedAt
    ) {
    }

    public record ActivityRow(
            long userId,
            String displayName,
            String role,
            int completedDays,
            int totalCorrect,
            int perfectCount,
            int recoveredCount,
            int fullyLitCount,
            boolean todayCompleted,
            int activityScore
    ) {
    }

    public record InviteRow(
            long inviteId,
            long groupId,
            Instant expiresAt,
            Integer maxUses,
            int usedCount
    ) {
    }

    public record InviteManagementRow(
            long inviteId,
            boolean enabled,
            Instant expiresAt,
            Integer maxUses,
            int usedCount,
            Instant createdAt
    ) {
    }
}
