package com.triread.api.auth;

import com.triread.api.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final LoginAttemptService loginAttemptService;
    private final SignupAttemptService signupAttemptService;
    private final SecurityContextRepository securityContextRepository;
    private final SessionInvalidationService sessionInvalidationService;

    public AuthController(
            AuthService authService,
            LoginAttemptService loginAttemptService,
            SignupAttemptService signupAttemptService,
            SecurityContextRepository securityContextRepository,
            SessionInvalidationService sessionInvalidationService
    ) {
        this.authService = authService;
        this.loginAttemptService = loginAttemptService;
        this.signupAttemptService = signupAttemptService;
        this.securityContextRepository = securityContextRepository;
        this.sessionInvalidationService = sessionInvalidationService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse signup(
            @Valid @RequestBody SignupRequest signupRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        signupAttemptService.checkAndRecord(request.getRemoteAddr());
        AuthService.AuthenticatedUser user = authService.register(
                signupRequest.loginName(),
                signupRequest.displayName(),
                signupRequest.pin()
        );
        startSession(user, request, response);
        return AuthResponse.from(user);
    }

    @PostMapping("/login")
    public AuthResponse login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String clientAddress = request.getRemoteAddr();
        AuthService.AuthenticatedUser user = authenticate(loginRequest, clientAddress);
        startSession(user, request, response);
        return AuthResponse.from(user);
    }

    @GetMapping("/me")
    public AuthResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        return AuthResponse.from(principal);
    }

    @PatchMapping("/pin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePin(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody ChangePinRequest changePinRequest,
            HttpServletRequest request
    ) {
        authService.changePin(
                principal.userId(),
                changePinRequest.currentPin(),
                changePinRequest.newPin()
        );
        endAllSessions(principal.userId(), request);
    }

    private AuthService.AuthenticatedUser authenticate(
            LoginRequest loginRequest,
            String clientAddress
    ) {
        loginAttemptService.assertAllowed(clientAddress, loginRequest.loginName());
        try {
            AuthService.AuthenticatedUser user =
                    authService.login(loginRequest.loginName(), loginRequest.pin());
            loginAttemptService.recordSuccess(clientAddress, loginRequest.loginName());
            return user;
        } catch (ApiException exception) {
            recordLoginFailure(clientAddress, loginRequest.loginName(), exception);
            throw exception;
        }
    }

    private void recordLoginFailure(
            String clientAddress,
            String loginName,
            ApiException exception
    ) {
        if ("INVALID_CREDENTIALS".equals(exception.getCode())) {
            loginAttemptService.recordFailure(clientAddress, loginName);
        }
    }

    private void startSession(
            AuthService.AuthenticatedUser user,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        invalidateCurrentSession(request);

        AuthPrincipal principal = AuthPrincipal.from(user);
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + principal.role()))
                );

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
        securityContextRepository.saveContext(securityContext, request, response);

        HttpSession session = request.getSession(false);
        if (session != null) {
            sessionInvalidationService.registerSession(session.getId(), principal);
        }
    }

    private void endAllSessions(long userId, HttpServletRequest request) {
        sessionInvalidationService.invalidateUser(userId);
        invalidateCurrentSession(request);
        SecurityContextHolder.clearContext();
    }

    private void invalidateCurrentSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    public record SignupRequest(
            @NotBlank
            @Size(min = 4, max = 30)
            @Pattern(
                    regexp = "[A-Za-z0-9._-]+",
                    message = "Only letters, numbers, dot, underscore, and hyphen are allowed."
            )
            String loginName,

            @NotBlank
            @Size(max = 30)
            String displayName,

            @NotBlank
            @Pattern(regexp = "\\d{4,12}", message = "PIN must contain 4 to 12 digits.")
            String pin
    ) {
    }

    public record LoginRequest(
            @NotBlank
            @Size(max = 30)
            String loginName,

            @NotBlank
            @Pattern(regexp = "\\d{4,12}", message = "PIN must contain 4 to 12 digits.")
            String pin
    ) {
    }

    public record ChangePinRequest(
            @NotBlank
            @Pattern(regexp = "\\d{4,12}", message = "PIN must contain 4 to 12 digits.")
            String currentPin,

            @NotBlank
            @Pattern(regexp = "\\d{4,12}", message = "PIN must contain 4 to 12 digits.")
            String newPin
    ) {
    }

    public record AuthResponse(
            long userId,
            String loginName,
            String displayName,
            String role
    ) {
        public static AuthResponse from(AuthService.AuthenticatedUser user) {
            return new AuthResponse(
                    user.userId(),
                    user.loginName(),
                    user.displayName(),
                    user.role()
            );
        }

        public static AuthResponse from(AuthPrincipal principal) {
            return new AuthResponse(
                    principal.userId(),
                    principal.loginName(),
                    principal.displayName(),
                    principal.role()
            );
        }
    }
}
