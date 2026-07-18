package com.triread.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.triread.api.common.ApiException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {
    @Mock AuthMapper authMapper;

    @Test
    void listsUsersWithoutExposingPinHashes() {
        AdminUserService service = new AdminUserService(authMapper);
        when(authMapper.countAll()).thenReturn(1L);
        when(authMapper.findAll(0, 10)).thenReturn(List.of(user(2L, "reader", "USER")));

        assertThat(service.getUsers(0, 10).items()).singleElement().satisfies(summary -> {
            assertThat(summary.loginName()).isEqualTo("reader");
            assertThat(summary.role()).isEqualTo("USER");
        });
    }

    @Test
    void grantsAdministratorRole() {
        AdminUserService service = new AdminUserService(authMapper);
        when(authMapper.findById(2L)).thenReturn(user(2L, "reader", "USER"));
        when(authMapper.updateRole(2L, "ADMIN")).thenReturn(1);

        AdminUserService.UserSummary result = service.updateRole(1L, 2L, "admin");

        assertThat(result.role()).isEqualTo("ADMIN");
        verify(authMapper).updateRole(2L, "ADMIN");
    }

    @Test
    void rejectsSelfDemotion() {
        AdminUserService service = new AdminUserService(authMapper);
        when(authMapper.findById(1L)).thenReturn(user(1L, "owner", "ADMIN"));

        assertThatThrownBy(() -> service.updateRole(1L, 1L, "USER"))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("CANNOT_DEMOTE_SELF"));
        verify(authMapper, never()).updateRole(1L, "USER");
    }

    @Test
    void preservesLastAdministrator() {
        AdminUserService service = new AdminUserService(authMapper);
        when(authMapper.findById(2L)).thenReturn(user(2L, "owner", "ADMIN"));
        when(authMapper.countEnabledAdmins()).thenReturn(1);

        assertThatThrownBy(() -> service.updateRole(1L, 2L, "USER"))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("LAST_ADMIN_REQUIRED"));
    }

    private AuthUser user(long id, String loginName, String role) {
        AuthUser user = new AuthUser();
        user.setId(id);
        user.setLoginName(loginName);
        user.setDisplayName(loginName);
        user.setAppRole(role);
        user.setEnabled(true);
        user.setCreatedAt(Instant.parse("2026-07-18T00:00:00Z"));
        return user;
    }
}
