package com.triread.api.common;

import org.springframework.http.HttpStatus;

public class RateLimitException extends ApiException {

    private final long retryAfterSeconds;

    public RateLimitException(String code, String message, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, code, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
