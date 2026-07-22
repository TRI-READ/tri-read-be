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
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {
    @Mock AuthMapper authMapper;
    @Mock PasswordEncoder passwordEncoder;
    @Mock SessionInvalidationService sessionInvalidationService;

    private AdminUserService service() {
        return new AdminUserService(authMapper, passwordEncoder, sessionInvalidationService);
    }

    @Test
    void listsUsersWithoutExposingPinHashes() {
        AdminUserService service = service();
        when(authMapper.countAll()).thenReturn(1L);
        when(authMapper.findAll(0, 10)).thenReturn(List.of(user(2L, "reader", "USER")));

        assertThat(service.getUsers(0, 10).items()).singleElement().satisfies(summary -> {
            assertThat(summary.loginName()).isEqualTo("reader");
            assertThat(summary.role()).isEqualTo("USER");
        });
    }

    @Test
    void grantsAdministratorRole() {
        AdminUserService service = service();
        when(authMapper.findById(2L)).thenReturn(user(2L, "reader", "USER"));
        when(authMapper.updateRole(2L, "ADMIN")).thenReturn(1);

        AdminUserService.UserSummary result = service.updateRole(1L, 2L, "admin");

        assertThat(result.role()).isEqualTo("ADMIN");
        verify(authMapper).updateRole(2L, "ADMIN");
    }

    @Test
    void rejectsSelfDemotion() {
        AdminUserService service = service();
        when(authMapper.findById(1L)).thenReturn(user(1L, "owner", "ADMIN"));

        assertThatThrownBy(() -> service.updateRole(1L, 1L, "USER"))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("CANNOT_DEMOTE_SELF"));
        verify(authMapper, never()).updateRole(1L, "USER");
    }

    @Test
    void preservesLastAdministrator() {
        AdminUserService service = service();
        when(authMapper.findById(2L)).thenReturn(user(2L, "owner", "ADMIN"));
        when(authMapper.countEnabledAdmins()).thenReturn(1);

        assertThatThrownBy(() -> service.updateRole(1L, 2L, "USER"))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("LAST_ADMIN_REQUIRED"));
    }

    @Test
    void disablesUserAndInvalidatesExistingSessions() {
        AdminUserService service = service();
        when(authMapper.findById(2L)).thenReturn(user(2L, "reader", "USER"));
        when(authMapper.updateEnabled(2L, false)).thenReturn(1);

        AdminUserService.UserSummary result = service.updateEnabled(1L, 2L, false);

        assertThat(result.enabled()).isFalse();
        verify(sessionInvalidationService).invalidateUser(2L);
    }

    @Test
    void rejectsDisablingCurrentAdministrator() {
        AdminUserService service = service();
        when(authMapper.findById(1L)).thenReturn(user(1L, "owner", "ADMIN"));

        assertThatThrownBy(() -> service.updateEnabled(1L, 1L, false))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("CANNOT_DISABLE_SELF"));
        verify(authMapper, never()).updateEnabled(1L, false);
    }

    @Test
    void resetsPinAndInvalidatesExistingSessions() {
        AdminUserService service = service();
        when(authMapper.findById(2L)).thenReturn(user(2L, "reader", "USER"));
        when(passwordEncoder.encode("5678")).thenReturn("new-hashed-pin");
        when(authMapper.updatePinHash(2L, "new-hashed-pin")).thenReturn(1);
        when(sessionInvalidationService.invalidateUser(2L)).thenReturn(3);

        assertThat(service.resetPin(2L, "5678")).isEqualTo(3);
        verify(authMapper).updatePinHash(2L, "new-hashed-pin");
        verify(sessionInvalidationService).invalidateUser(2L);
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
