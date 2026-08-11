package com.triread.api.audit;

import com.triread.api.common.PageResponse;
import java.util.Map;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
    public PageResponse<AdminAuditData.AuditRow> getLogs(int requestedPage, int requestedSize,
                                                         String action, String actor,
                                                         LocalDate from, LocalDate to) {
        int page = PageResponse.page(requestedPage);
        int size = PageResponse.size(requestedSize);
        String normalizedAction = text(action);
        String normalizedActor = text(actor);
        ZoneId seoul = ZoneId.of("Asia/Seoul");
        Instant fromInstant = from == null ? null : from.atStartOfDay(seoul).toInstant();
        Instant untilInstant = to == null ? null : to.plusDays(1).atStartOfDay(seoul).toInstant();
        return PageResponse.of(
                mapper.findAll(normalizedAction, normalizedActor, fromInstant, untilInstant,
                        page * size, size),
                page,
                size,
                mapper.countAll(normalizedAction, normalizedActor, fromInstant, untilInstant)
        );
    }

    private String text(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String json(Map<String, ?> details) {
        try {
            return objectMapper.writeValueAsString(details == null ? Map.of() : details);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Audit details could not be serialized", exception);
        }
    }
}
