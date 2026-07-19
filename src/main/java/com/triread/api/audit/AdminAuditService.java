package com.triread.api.audit;

import com.triread.api.common.PageResponse;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class AdminAuditService {
    private final AdminAuditMapper mapper;
    private final ObjectMapper objectMapper;

    public AdminAuditService(AdminAuditMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public void record(long actorUserId, String action, String targetType,
                       Object targetId, Map<String, ?> details) {
        mapper.insert(new AdminAuditData.AuditInsert(actorUserId, action, targetType,
                targetId == null ? null : String.valueOf(targetId), json(details)));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminAuditData.AuditRow> getLogs(int requestedPage, int requestedSize) {
        int page = PageResponse.page(requestedPage);
        int size = PageResponse.size(requestedSize);
        return PageResponse.of(mapper.findAll(page * size, size), page, size, mapper.countAll());
    }

    private String json(Map<String, ?> details) {
        try {
            return objectMapper.writeValueAsString(details == null ? Map.of() : details);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Audit details could not be serialized", exception);
        }
    }
}
