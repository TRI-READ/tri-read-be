package com.triread.api.generation;

import com.triread.api.common.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AiApiUsageService {
    private final QuizGenerationMapper mapper;
    private final QuizGenerationProperties properties;
    private final Clock clock;

    public AiApiUsageService(QuizGenerationMapper mapper,
                             QuizGenerationProperties properties,
                             Clock clock) {
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
    }

    public synchronized long start(long generationLogId, String provider,
                                   String model, String purpose) {
        TimeRange today = today();
        if (mapper.countApiCallsCreatedBetween(today.from(), today.until())
                >= Math.max(1, properties.getMaxApiCallsPerDay())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "QUIZ_GENERATION_API_DAILY_LIMIT_REACHED",
                    "The daily AI API call budget has been exhausted.");
        }
        QuizGenerationData.ApiCallInsert call = new QuizGenerationData.ApiCallInsert(
                generationLogId, provider, model, purpose);
        mapper.insertApiCall(call);
        return call.getId();
    }

    public void success(long apiCallId) {
        mapper.completeApiCall(apiCallId, "SUCCESS", null, clock.instant());
    }

    public void failure(long apiCallId, String errorCode) {
        mapper.completeApiCall(apiCallId, "FAILED", errorCode, clock.instant());
    }

    public TodayUsage todayUsage() {
        TimeRange today = today();
        QuizGenerationData.ApiUsageStats stats = mapper.getApiUsageStats(today.from(), today.until());
        return new TodayUsage(stats.totalCount(), stats.successCount(), stats.failureCount(),
                Math.max(1, properties.getMaxApiCallsPerDay()));
    }

    private TimeRange today() {
        LocalDate today = LocalDate.now(clock);
        Instant from = today.atStartOfDay(clock.getZone()).toInstant();
        Instant until = today.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        return new TimeRange(from, until);
    }

    private record TimeRange(Instant from, Instant until) {}
    public record TodayUsage(long totalCount, long successCount, long failureCount, long limit) {}
}
