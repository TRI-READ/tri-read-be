package com.triread.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

class AuthControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final LoginAttemptService loginAttemptService = mock(LoginAttemptService.class);
    private final HttpSessionSecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();
    private final AuthController authController =
            new AuthController(authService, loginAttemptService, securityContextRepository);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void signupCreatesAuthenticatedSession() {
        AuthService.AuthenticatedUser user =
                new AuthService.AuthenticatedUser(3L, "reader", "Reader", "USER");
        when(authService.register("reader", "Reader", "1234")).thenReturn(user);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<AuthController.AuthResponse> result = authController.signup(
                new AuthController.SignupRequest("reader", "Reader", "1234"),
                request,
                response
        );

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isEqualTo(
                new AuthController.AuthResponse(3L, "reader", "Reader", "USER")
        );

        SecurityContext savedContext = (SecurityContext) request.getSession(false).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY
        );
        assertThat(savedContext.getAuthentication().isAuthenticated()).isTrue();
        assertThat(savedContext.getAuthentication().getPrincipal())
                .isEqualTo(new AuthPrincipal(3L, "reader", "Reader", "USER"));
    }
}
