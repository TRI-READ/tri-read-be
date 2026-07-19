package com.triread.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final LoginAttemptService loginAttemptService;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(
            AuthService authService,
            LoginAttemptService loginAttemptService,
            SecurityContextRepository securityContextRepository
    ) {
        this.authService = authService;
        this.loginAttemptService = loginAttemptService;
        this.securityContextRepository = securityContextRepository;
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(
            @Valid @RequestBody SignupRequest signupRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        AuthService.AuthenticatedUser user = authService.register(
                signupRequest.loginName(),
                signupRequest.displayName(),
                signupRequest.pin()
        );
        saveAuthentication(user, request, response);
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse.from(user));
    }

    @PostMapping("/login")
    public AuthResponse login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String clientAddress = request.getRemoteAddr();
        loginAttemptService.assertAllowed(clientAddress, loginRequest.loginName());
        AuthService.AuthenticatedUser user;
        try {
            user = authService.login(loginRequest.loginName(), loginRequest.pin());
        } catch (com.triread.api.common.ApiException exception) {
            if ("INVALID_CREDENTIALS".equals(exception.getCode())) {
                loginAttemptService.recordFailure(clientAddress, loginRequest.loginName());
            }
            throw exception;
        }
        loginAttemptService.recordSuccess(clientAddress, loginRequest.loginName());
        saveAuthentication(user, request, response);
        return AuthResponse.from(user);
    }

    @GetMapping("/me")
    public AuthResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        return AuthResponse.from(principal);
    }

    private void saveAuthentication(
            AuthService.AuthenticatedUser user,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        HttpSession existingSession = request.getSession(false);
        if (existingSession != null) {
            existingSession.invalidate();
        }

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
