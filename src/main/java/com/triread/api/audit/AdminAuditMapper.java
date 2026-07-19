package com.triread.api.audit;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminAuditMapper {
    void insert(AdminAuditData.AuditInsert audit);
    List<AdminAuditData.AuditRow> findAll(@Param("offset") int offset, @Param("limit") int limit);
    long countAll();
}
