package com.triread.api.operations;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OperationsMapper {
    Long databaseSizeBytes();
    OperationsData.AiStats aiStats(@Param("from") Instant from, @Param("until") Instant until);
    List<OperationsData.ErrorCount> aiErrors(@Param("from") Instant from,
                                             @Param("until") Instant until);
    OperationsData.QualityStats qualityStats(@Param("from") Instant from);
    List<OperationsData.InventoryRow> inventory(@Param("from") LocalDate from,
                                                @Param("until") LocalDate until,
                                                @Param("requiredCount") int requiredCount);
    List<OperationsData.FailureRow> recentFailures(int limit);
    List<OperationsData.AuditRow> recentAdminActions(int limit);
    OperationsData.OperationEventRow lastEvent(String eventType);
    long countGroundedBriefs();
    long countGroundedSources();
    void insertEvent(OperationsData.EventInsert event);
    void completeEvent(@Param("eventId") long eventId, @Param("status") String status,
                       @Param("message") String message, @Param("completedAt") Instant completedAt);
}
