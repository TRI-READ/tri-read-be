package com.triread.api.auth;

import com.triread.api.common.ApiException;
import com.triread.api.common.PageResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
public class AdminUserService {
    private static final Set<String> ROLES = Set.of("USER", "ADMIN");

    private final AuthMapper authMapper;
    private final PasswordEncoder passwordEncoder;
    private final SessionInvalidationService sessionInvalidationService;

    public AdminUserService(AuthMapper authMapper, PasswordEncoder passwordEncoder,
                            SessionInvalidationService sessionInvalidationService) {
        this.authMapper = authMapper;
        this.passwordEncoder = passwordEncoder;
        this.sessionInvalidationService = sessionInvalidationService;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSummary> getUsers(int requestedPage, int requestedSize) {
        int page = PageResponse.page(requestedPage);
        int size = PageResponse.size(requestedSize);
        long total = authMapper.countAll();
        List<UserSummary> users = new ArrayList<>();
        for (AuthUser user : authMapper.findAll(page * size, size)) {
            users.add(UserSummary.from(user));
        }
        return PageResponse.of(users, page, size, total);
    }

    @Transactional
    public UserSummary updateRole(long currentAdminId, long userId, String role) {
        String newRole = normalizeRole(role);
        AuthUser user = requireEnabledUser(userId);
        validateRoleChange(currentAdminId, user, newRole);

        if (newRole.equals(user.getAppRole())) {
            return UserSummary.from(user);
        }

        saveRole(user, newRole);
        return UserSummary.from(user);
    }

    @Transactional
    public UserSummary updateEnabled(long currentAdminId, long userId, boolean enabled) {
        AuthUser user = requireUser(userId);
        validateStatusChange(currentAdminId, user, enabled);

        if (user.isEnabled() == enabled) {
            return UserSummary.from(user);
        }

        saveEnabled(user, enabled);
        return UserSummary.from(user);
    }

    @Transactional
    public int resetPin(long userId, String newPin) {
        AuthUser target = requireUser(userId);
        if (!target.isEnabled()) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_DISABLED",
                    "Enable the user before resetting the PIN.");
        }
        if (authMapper.updatePinHash(userId, passwordEncoder.encode(newPin)) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "PIN_RESET_FAILED",
                    "The PIN could not be reset.");
        }
        return sessionInvalidationService.invalidateUser(userId);
    }

    private String normalizeRole(String role) {
        String normalizedRole = role == null ? "" : role.trim().toUpperCase();
        if (!ROLES.contains(normalizedRole)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_APP_ROLE",
                    "The application role must be USER or ADMIN.");
        }
        return normalizedRole;
    }

    private AuthUser requireEnabledUser(long userId) {
        AuthUser user = requireUser(userId);
        if (!user.isEnabled()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND",
                    "The user was not found.");
        }
        return user;
    }

    private void validateRoleChange(long currentAdminId, AuthUser user, String newRole) {
        boolean demotingToUser = "ADMIN".equals(user.getAppRole()) && "USER".equals(newRole);
        if (!demotingToUser) {
            return;
        }
        if (currentAdminId == user.getId()) {
            throw new ApiException(HttpStatus.CONFLICT, "CANNOT_DEMOTE_SELF",
                    "An administrator cannot demote their own active session.");
        }
        if (isLastEnabledAdmin(user)) {
            throw new ApiException(HttpStatus.CONFLICT, "LAST_ADMIN_REQUIRED",
                    "At least one enabled administrator is required.");
        }
    }

    private void validateStatusChange(long currentAdminId, AuthUser user, boolean enabled) {
        if (enabled) {
            return;
        }
        if (currentAdminId == user.getId()) {
            throw new ApiException(HttpStatus.CONFLICT, "CANNOT_DISABLE_SELF",
                    "An administrator cannot disable their own account.");
        }
        if (isLastEnabledAdmin(user)) {
            throw new ApiException(HttpStatus.CONFLICT, "LAST_ADMIN_REQUIRED",
                    "At least one enabled administrator is required.");
        }
    }

    private boolean isLastEnabledAdmin(AuthUser user) {
        return user.isEnabled()
                && "ADMIN".equals(user.getAppRole())
                && authMapper.countEnabledAdmins() <= 1;
    }

    private void saveRole(AuthUser user, String role) {
        if (authMapper.updateRole(user.getId(), role) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "APP_ROLE_UPDATE_FAILED",
                    "The application role could not be updated.");
        }
        user.setAppRole(role);
        sessionInvalidationService.invalidateUser(user.getId());
    }

    private void saveEnabled(AuthUser user, boolean enabled) {
        if (authMapper.updateEnabled(user.getId(), enabled) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_STATUS_UPDATE_FAILED",
                    "The user status could not be updated.");
        }
        user.setEnabled(enabled);
        sessionInvalidationService.invalidateUser(user.getId());
    }

    private AuthUser requireUser(long userId) {
        AuthUser user = authMapper.findById(userId);
        if (user == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "The user was not found.");
        }
        return user;
    }

    public record UserSummary(long userId, String loginName, String displayName,
                              String role, boolean enabled, Instant createdAt,
                              Instant lastLoginAt) {
        static UserSummary from(AuthUser user) {
            return new UserSummary(user.getId(), user.getLoginName(), user.getDisplayName(),
                    user.getAppRole(), user.isEnabled(), user.getCreatedAt(), user.getLastLoginAt());
        }
    }
}
