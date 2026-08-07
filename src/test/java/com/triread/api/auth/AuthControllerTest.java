package com.triread.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.triread.api.common.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

class AuthControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final LoginAttemptService loginAttemptService = mock(LoginAttemptService.class);
    private final HttpSessionSecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();
    private final SessionInvalidationService sessionInvalidationService =
            mock(SessionInvalidationService.class);
    private final AuthController authController =
            new AuthController(authService, loginAttemptService, securityContextRepository,
                    sessionInvalidationService);

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

        AuthController.AuthResponse result = authController.signup(
                new AuthController.SignupRequest("reader", "Reader", "1234"),
                request,
                response
        );

        assertThat(result).isEqualTo(
                new AuthController.AuthResponse(3L, "reader", "Reader", "USER")
        );

        SecurityContext savedContext = (SecurityContext) request.getSession(false).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY
        );
        assertThat(savedContext.getAuthentication().isAuthenticated()).isTrue();
        assertThat(savedContext.getAuthentication().getPrincipal())
                .isEqualTo(new AuthPrincipal(3L, "reader", "Reader", "USER"));
        verify(sessionInvalidationService).registerSession(
                request.getSession(false).getId(),
                new AuthPrincipal(3L, "reader", "Reader", "USER"));
    }

    @Test
    void loginRecordsInvalidCredentialFailure() {
        AuthController.LoginRequest loginRequest =
                new AuthController.LoginRequest("reader", "9999");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        doThrow(new ApiException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "Login name or PIN is incorrect."
        )).when(authService).login("reader", "9999");

        assertThatThrownBy(() -> authController.login(loginRequest, request, response))
                .isInstanceOf(ApiException.class);

        verify(loginAttemptService).assertAllowed("127.0.0.1", "reader");
        verify(loginAttemptService).recordFailure("127.0.0.1", "reader");
    }

    @Test
    void changePinExpiresEveryUserSessionAndCurrentSession() {
        AuthPrincipal principal = new AuthPrincipal(3L, "reader", "Reader", "USER");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = (MockHttpSession) request.getSession(true);

        authController.changePin(
                principal,
                new AuthController.ChangePinRequest("1234", "5678"),
                request
        );

        assertThat(session.isInvalid()).isTrue();
        verify(authService).changePin(3L, "1234", "5678");
        verify(sessionInvalidationService).invalidateUser(3L);
    }
}
