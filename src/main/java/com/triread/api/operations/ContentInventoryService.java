package com.triread.api.operations;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContentInventoryService {
    private static final int USER_DETAIL_LIMIT = 20;
    private final OperationsMapper mapper;
    private final ContentInventoryProperties properties;
    private final Clock clock;

    public ContentInventoryService(OperationsMapper mapper,
                                   ContentInventoryProperties properties, Clock clock) {
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OperationsData.ContentInventory inventory() {
        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();
        Instant activeSince = now.minus(properties.getActiveWindowDays(), ChronoUnit.DAYS);
        Instant pendingBefore = now.minus(properties.getReviewWaitHours(), ChronoUnit.HOURS);
        OperationsData.PoolInventory pool = mapper.poolInventory(today, pendingBefore);
        OperationsData.UserInventoryStats stats = mapper.userInventoryStats(
                today, activeSince, properties.getWarningThreshold());
        List<OperationsData.UserInventory> users = mapper.lowestStockUsers(
                today, activeSince, USER_DETAIL_LIMIT);

        String status = stockStatus(stats);
        List<OperationsData.InventoryAlert> alerts = new ArrayList<>();
        if ("LOW".equals(status) || "CRITICAL".equals(status)) {
            alerts.add(new OperationsData.InventoryAlert("LOW_USER_INVENTORY",
                    "CRITICAL".equals(status) ? "CRITICAL" : "WARNING",
                    "활성 사용자 최저 재고가 " + stats.minimumRemainingSets()
                            + "세트입니다. 검수·발행 대기 콘텐츠와 보충 필요 여부를 확인해 주세요."));
        }
        if (pool.overduePendingCount() > 0) {
            alerts.add(new OperationsData.InventoryAlert("OVERDUE_PUBLICATION", "WARNING",
                    "사용 가능일이 되었지만 생성 후 " + properties.getReviewWaitHours()
                            + "시간 이상 미발행인 세트가 " + pool.overduePendingCount() + "개 있습니다."));
        }
        return new OperationsData.ContentInventory(today, properties.getActiveWindowDays(),
                properties.getWarningThreshold(), properties.getCriticalThreshold(),
                properties.getReviewWaitHours(), status, stats, pool, users, List.copyOf(alerts));
    }

    private String stockStatus(OperationsData.UserInventoryStats stats) {
        if (stats.activeUserCount() == 0) return "NO_ACTIVE_USERS";
        if (stats.minimumRemainingSets() < properties.getCriticalThreshold()) return "CRITICAL";
        if (stats.minimumRemainingSets() < properties.getWarningThreshold()) return "LOW";
        return "HEALTHY";
    }
}
