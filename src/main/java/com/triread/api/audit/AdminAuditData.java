package com.triread.api.audit;

import java.time.Instant;

public final class AdminAuditData {
    private AdminAuditData() {}

    public record AuditInsert(long actorUserId, String action, String targetType,
                              String targetId, String detailsJson) {}
    public record AuditRow(long auditLogId, Long actorUserId, String actorLoginName,
                           String action, String targetType, String targetId,
                           String detailsJson, Instant createdAt) {}
}
