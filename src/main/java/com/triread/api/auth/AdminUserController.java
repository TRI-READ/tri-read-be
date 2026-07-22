package com.triread.api.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import com.triread.api.common.PageResponse;
import com.triread.api.audit.AdminAuditService;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final AdminUserService service;
    private final AdminAuditService auditService;

    public AdminUserController(AdminUserService service, AdminAuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    @GetMapping
    public PageResponse<AdminUserService.UserSummary> users(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return service.getUsers(page, size);
    }

    @PatchMapping("/{userId}/role")
    public AdminUserService.UserSummary updateRole(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Positive long userId,
            @Valid @RequestBody UpdateRoleRequest request
    ) {
        AdminUserService.UserSummary updated = service.updateRole(
                principal.userId(), userId, request.role());
        auditService.record(principal.userId(), "USER_ROLE_UPDATED", "USER", userId,
                Map.of("role", updated.role()));
        return updated;
    }

    @PatchMapping("/{userId}/enabled")
    public AdminUserService.UserSummary updateEnabled(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Positive long userId,
            @Valid @RequestBody UpdateEnabledRequest request
    ) {
        AdminUserService.UserSummary updated = service.updateEnabled(
                principal.userId(), userId, request.enabled());
        auditService.record(principal.userId(), "USER_STATUS_UPDATED", "USER", userId,
                Map.of("enabled", updated.enabled()));
        return updated;
    }

    @PatchMapping("/{userId}/pin")
    public void resetPin(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Positive long userId,
            @Valid @RequestBody ResetPinRequest request
    ) {
        int invalidatedSessions = service.resetPin(userId, request.newPin());
        auditService.record(principal.userId(), "USER_PIN_RESET", "USER", userId,
                Map.of("invalidatedSessions", invalidatedSessions));
    }

    public record UpdateRoleRequest(@NotBlank String role) {}

    public record UpdateEnabledRequest(boolean enabled) {}

    public record ResetPinRequest(
            @NotBlank
            @Pattern(regexp = "\\d{4,12}", message = "PIN must contain 4 to 12 digits.")
            String newPin
    ) {}
}
