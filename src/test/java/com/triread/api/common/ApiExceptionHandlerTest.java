package com.triread.api.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

class ApiExceptionHandlerTest {

    @Test
    void rateLimitResponseIncludesRetryAfterHeader() {
        ApiExceptionHandler handler = new ApiExceptionHandler();

        ResponseEntity<ApiExceptionHandler.ApiErrorResponse> response =
                handler.handleRateLimitException(new RateLimitException(
                        "TOO_MANY_SIGNUP_ATTEMPTS",
                        "Too many signup attempts.",
                        120
                ));

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("120");
        assertThat(response.getBody().code()).isEqualTo("TOO_MANY_SIGNUP_ATTEMPTS");
    }
}
