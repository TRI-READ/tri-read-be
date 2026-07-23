package com.triread.api.operations;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class OperationsData {
    private OperationsData() {}

    public record AiStats(long totalCount, long successCount, long failureCount,
                          Double averageLatencyMs) {}
    public record ErrorCount(String errorCode, long count) {}
    public record QualityStats(long completedCount, long publishedCount, long failedCount,
                               long retryCount, long duplicateRejectedCount,
                               Double averageValidationScore) {
        public double retryRate() {
            return completedCount == 0 ? 0 : retryCount * 100.0 / completedCount;
        }
        public double failureRate() {
            return completedCount == 0 ? 0 : failedCount * 100.0 / completedCount;
        }
        public double duplicateRejectionRate() {
            return completedCount == 0 ? 0 : duplicateRejectedCount * 100.0 / completedCount;
        }
    }
    public record InventoryRow(LocalDate challengeDate, long publishedCount, int requiredCount) {
        public boolean shortage() { return publishedCount < requiredCount; }
    }
    public record FailureRow(long generationLogId, LocalDate targetDate, String status,
                             String errorMessage, Instant updatedAt) {}
    public record OperationEventRow(long operationEventId, String eventType, String status,
                                    String message, Instant startedAt, Instant completedAt) {}
    public record AuditRow(long auditLogId, String actorLoginName, String action,
                           String targetType, String targetId, Instant createdAt) {}

    public record Summary(
            String applicationStatus,
            String databaseStatus,
            long databaseSizeBytes,
            long uptimeSeconds,
            String version,
            Instant startedAt,
            AiStats aiToday,
            List<ErrorCount> aiErrorsToday,
            QualityStats quality,
            List<InventoryRow> inventory,
            List<FailureRow> recentFailures,
            List<AuditRow> recentAdminActions,
            int activeLoginLocks,
            OperationEventRow lastSchedulerRun,
            Instant nextSchedulerRun,
            OperationEventRow lastBackupRun,
            long groundedBriefCount,
            long groundedSourceCount
    ) {}

    public static final class EventInsert {
        private Long id;
        private final String eventType;
        private final String status;
        private final String message;
        public EventInsert(String eventType, String status, String message) {
            this.eventType = eventType;
            this.status = status;
            this.message = message;
        }
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getEventType() { return eventType; }
        public String getStatus() { return status; }
        public String getMessage() { return message; }
    }
}
