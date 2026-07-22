package com.triread.api.auth;

import com.triread.api.common.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String DEFAULT_ROLE = "USER";

    private final AuthMapper authMapper;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AuthService(
            AuthMapper authMapper,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.authMapper = authMapper;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public AuthenticatedUser register(String loginName, String displayName, String pin) {
        String normalizedLoginName = normalizeLoginName(loginName);
        if (authMapper.findByLoginName(normalizedLoginName) != null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "LOGIN_NAME_ALREADY_EXISTS",
                    "This login name is already in use."
            );
        }

        AuthUser user = new AuthUser();
        user.setLoginName(normalizedLoginName);
        user.setDisplayName(displayName.trim());
        user.setPinHash(passwordEncoder.encode(pin));
        user.setAppRole(DEFAULT_ROLE);
        user.setEnabled(true);

        try {
            authMapper.insert(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "LOGIN_NAME_ALREADY_EXISTS",
                    "This login name is already in use."
            );
        }

        Instant loggedInAt = clock.instant();
        authMapper.updateLastLoginAt(user.getId(), loggedInAt);
        return toAuthenticatedUser(user);
    }

    @Transactional
    public AuthenticatedUser login(String loginName, String pin) {
        AuthUser user = authMapper.findByLoginName(normalizeLoginName(loginName));
        if (user == null || !user.isEnabled() || !passwordEncoder.matches(pin, user.getPinHash())) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_CREDENTIALS",
                    "Login name or PIN is incorrect."
            );
        }

        authMapper.updateLastLoginAt(user.getId(), clock.instant());
        return toAuthenticatedUser(user);
    }

    @Transactional
    public void changePin(long userId, String currentPin, String newPin) {
        AuthUser user = authMapper.findById(userId);
        if (user == null || !user.isEnabled()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "The user was not found.");
        }
        if (!passwordEncoder.matches(currentPin, user.getPinHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENT_PIN_INCORRECT",
                    "The current PIN is incorrect.");
        }
        if (passwordEncoder.matches(newPin, user.getPinHash())) {
            throw new ApiException(HttpStatus.CONFLICT, "PIN_REUSE_NOT_ALLOWED",
                    "The new PIN must be different from the current PIN.");
        }
        if (authMapper.updatePinHash(userId, passwordEncoder.encode(newPin)) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "PIN_CHANGE_FAILED",
                    "The PIN could not be changed.");
        }
    }

    private String normalizeLoginName(String loginName) {
        return loginName.trim().toLowerCase(Locale.ROOT);
    }

    private AuthenticatedUser toAuthenticatedUser(AuthUser user) {
        return new AuthenticatedUser(
                user.getId(),
                user.getLoginName(),
                user.getDisplayName(),
                user.getAppRole()
        );
    }

    public record AuthenticatedUser(
            long userId,
            String loginName,
            String displayName,
            String role
    ) {
    }
}
