package com.triread.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.triread.api.audit.AdminAuditService;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdminControllerTest {

    private final AdminUserService userService = mock(AdminUserService.class);
    private final LoginAttemptService loginAttemptService = mock(LoginAttemptService.class);
    private final AdminAuditService auditService = mock(AdminAuditService.class);
    private final AuthPrincipal admin = new AuthPrincipal(1L, "admin", "Admin", "ADMIN");

    @Test
    void roleUpdateRecordsAuditLog() {
        AdminUserController controller = new AdminUserController(userService, auditService);
        AdminUserService.UserSummary updated = new AdminUserService.UserSummary(
                2L, "reader", "Reader", "ADMIN", true, Instant.EPOCH, null);
        when(userService.updateRole(1L, 2L, "ADMIN")).thenReturn(updated);

        AdminUserService.UserSummary result = controller.updateRole(
                admin,
                2L,
                new AdminUserController.UpdateRoleRequest("ADMIN")
        );

        assertThat(result).isEqualTo(updated);
        verify(auditService).record(
                1L,
                "USER_ROLE_UPDATED",
                "USER",
                2L,
                Map.of("role", "ADMIN")
        );
    }

    @Test
    void pinResetRecordsInvalidatedSessionCount() {
        AdminUserController controller = new AdminUserController(userService, auditService);
        when(userService.resetPin(2L, "5678")).thenReturn(3);

        controller.resetPin(
                admin,
                2L,
                new AdminUserController.ResetPinRequest("5678")
        );

        verify(auditService).record(
                1L,
                "USER_PIN_RESET",
                "USER",
                2L,
                Map.of("invalidatedSessions", 3)
        );
    }

    @Test
    void loginUnlockReturnsAndRecordsClearedCount() {
        AdminSecurityController controller =
                new AdminSecurityController(loginAttemptService, auditService);
        when(loginAttemptService.clearLogin("reader")).thenReturn(2);

        AdminSecurityController.UnlockResult result = controller.unlock(admin, "reader");

        assertThat(result.clearedEntries()).isEqualTo(2);
        verify(auditService).record(
                1L,
                "LOGIN_LOCK_CLEARED",
                "USER",
                "reader",
                Map.of("clearedEntries", 2)
        );
    }
}
