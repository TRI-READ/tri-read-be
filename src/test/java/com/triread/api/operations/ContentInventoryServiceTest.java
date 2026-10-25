package com.triread.api.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ContentInventoryServiceTest {
    private final OperationsMapper mapper = mock(OperationsMapper.class);
    private final ContentInventoryProperties properties = new ContentInventoryProperties();
    private final ContentInventoryService service = new ContentInventoryService(mapper, properties,
            Clock.fixed(Instant.parse("2030-01-07T03:00:00Z"), ZoneId.of("Asia/Seoul")));

    @ParameterizedTest
    @CsvSource({"0,CRITICAL", "6,CRITICAL", "7,LOW", "13,LOW", "14,HEALTHY", "30,HEALTHY"})
    void classifiesMinimumStockAtThresholdBoundaries(long remaining, String expectedStatus) {
        givenInventory(new OperationsData.UserInventoryStats(2, remaining, remaining < 14 ? 1 : 0), 0);
        OperationsData.ContentInventory result = service.inventory();
        assertThat(result.status()).isEqualTo(expectedStatus);
        assertThat(result.alerts()).hasSize("HEALTHY".equals(expectedStatus) ? 0 : 1);
    }

    @Test
    void noActiveUsersIsNotZeroStockButStillWarnsAboutOverduePublication() {
        givenInventory(new OperationsData.UserInventoryStats(0, null, 0), 3);
        OperationsData.ContentInventory result = service.inventory();
        assertThat(result.status()).isEqualTo("NO_ACTIVE_USERS");
        assertThat(result.users().minimumRemainingSets()).isNull();
        assertThat(result.alerts()).singleElement().satisfies(alert -> {
            assertThat(alert.code()).isEqualTo("OVERDUE_PUBLICATION");
            assertThat(alert.message()).contains("48시간", "3개");
        });
    }

    private void givenInventory(OperationsData.UserInventoryStats stats, long overdue) {
        when(mapper.poolInventory(any(), any())).thenReturn(
                new OperationsData.PoolInventory(3, 0, 20, 0, 0, overdue, null));
        when(mapper.userInventoryStats(any(), any(), anyInt())).thenReturn(stats);
        when(mapper.lowestStockUsers(any(), any(), anyInt())).thenReturn(List.of());
    }
}
