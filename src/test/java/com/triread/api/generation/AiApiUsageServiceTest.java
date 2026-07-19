package com.triread.api.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.triread.api.common.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiApiUsageServiceTest {
    @Mock QuizGenerationMapper mapper;
    private QuizGenerationProperties properties;
    private AiApiUsageService service;

    @BeforeEach
    void setUp() {
        properties = new QuizGenerationProperties();
        properties.setMaxApiCallsPerDay(2);
        service = new AiApiUsageService(mapper, properties,
                Clock.fixed(Instant.parse("2026-07-19T01:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void rejectsCallBeforeGeminiInvocationWhenDailyBudgetIsExhausted() {
        when(mapper.countApiCallsCreatedBetween(any(), any())).thenReturn(2L);

        assertThatThrownBy(() -> service.start(1L, "GEMINI", "flash", "GENERATION"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode())
                                .isEqualTo("QUIZ_GENERATION_API_DAILY_LIMIT_REACHED"));
        verify(mapper, never()).insertApiCall(any());
    }

    @Test
    void recordsCallWhenBudgetRemains() {
        when(mapper.countApiCallsCreatedBetween(any(), any())).thenReturn(1L);
        org.mockito.Mockito.doAnswer(invocation -> {
            QuizGenerationData.ApiCallInsert call = invocation.getArgument(0);
            call.setId(9L);
            return null;
        }).when(mapper).insertApiCall(any());

        assertThat(service.start(1L, "GEMINI", "flash", "GENERATION")).isEqualTo(9L);
    }
}
