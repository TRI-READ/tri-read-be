package com.triread.api.auth;

import com.triread.api.audit.AdminAuditService;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/security")
public class AdminSecurityController {
    private final LoginAttemptService loginAttemptService;
    private final AdminAuditService auditService;

    public AdminSecurityController(LoginAttemptService loginAttemptService,
                                   AdminAuditService auditService) {
        this.loginAttemptService = loginAttemptService;
        this.auditService = auditService;
    }

    @GetMapping("/login-locks")
    public List<LoginAttemptService.LoginLockSummary> loginLocks() {
        return loginAttemptService.getLockedAttempts();
    }

    @DeleteMapping("/login-locks/{loginName}")
    public UnlockResult unlock(@AuthenticationPrincipal AuthPrincipal principal,
                               @PathVariable @NotBlank String loginName) {
        int cleared = loginAttemptService.clearLogin(loginName);
        auditService.record(principal.userId(), "LOGIN_LOCK_CLEARED", "USER", loginName,
                Map.of("clearedEntries", cleared));
        return new UnlockResult(cleared);
    }

    public record UnlockResult(int clearedEntries) {}
}
