package com.triread.api.audit;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminAuditMapper {
    void insert(AdminAuditData.AuditInsert audit);
    List<AdminAuditData.AuditRow> findAll(
            @Param("action") String action,
            @Param("actor") String actor,
            @Param("from") Instant from,
            @Param("until") Instant until,
            @Param("offset") int offset,
            @Param("limit") int limit
    );
    long countAll(
            @Param("action") String action,
            @Param("actor") String actor,
            @Param("from") Instant from,
            @Param("until") Instant until
    );
}
