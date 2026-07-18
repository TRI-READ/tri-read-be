package com.triread.api.auth;

import com.triread.api.common.ApiException;
import com.triread.api.common.PageResponse;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {
    private static final Set<String> ROLES = Set.of("USER", "ADMIN");

    private final AuthMapper authMapper;

    public AdminUserService(AuthMapper authMapper) {
        this.authMapper = authMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSummary> getUsers(int requestedPage, int requestedSize) {
        int page = PageResponse.page(requestedPage);
        int size = PageResponse.size(requestedSize);
        long total = authMapper.countAll();
        List<UserSummary> users = authMapper.findAll(page * size, size).stream()
                .map(UserSummary::from).toList();
        return PageResponse.of(users, page, size, total);
    }

    @Transactional
    public UserSummary updateRole(long currentAdminId, long userId, String role) {
        String normalizedRole = role == null ? "" : role.trim().toUpperCase();
        if (!ROLES.contains(normalizedRole)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_APP_ROLE",
                    "The application role must be USER or ADMIN.");
        }

        AuthUser target = authMapper.findById(userId);
        if (target == null || !target.isEnabled()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "The user was not found.");
        }
        if (currentAdminId == userId && "USER".equals(normalizedRole)) {
            throw new ApiException(HttpStatus.CONFLICT, "CANNOT_DEMOTE_SELF",
                    "An administrator cannot demote their own active session.");
        }
        if ("ADMIN".equals(target.getAppRole()) && "USER".equals(normalizedRole)
                && authMapper.countEnabledAdmins() <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "LAST_ADMIN_REQUIRED",
                    "At least one enabled administrator is required.");
        }
        if (!normalizedRole.equals(target.getAppRole())) {
            if (authMapper.updateRole(userId, normalizedRole) != 1) {
                throw new ApiException(HttpStatus.CONFLICT, "APP_ROLE_UPDATE_FAILED",
                        "The application role could not be updated.");
            }
            target.setAppRole(normalizedRole);
        }
        return UserSummary.from(target);
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
