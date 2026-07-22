package com.triread.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.triread.api.common.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-09T01:00:00Z");

    @Mock
    private AuthMapper authMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));
        authService = new AuthService(authMapper, passwordEncoder, clock);
    }

    @Test
    void registerNormalizesLoginNameAndHashesPin() {
        when(authMapper.findByLoginName("reader.one")).thenReturn(null);
        when(passwordEncoder.encode("1234")).thenReturn("hashed-pin");
        doAnswer(invocation -> {
            AuthUser user = invocation.getArgument(0);
            user.setId(7L);
            return 1;
        }).when(authMapper).insert(any(AuthUser.class));

        AuthService.AuthenticatedUser result =
                authService.register(" Reader.One ", "유원", "1234");

        assertThat(result.userId()).isEqualTo(7L);
        assertThat(result.loginName()).isEqualTo("reader.one");
        assertThat(result.displayName()).isEqualTo("유원");
        assertThat(result.role()).isEqualTo("USER");
        verify(authMapper).updateLastLoginAt(7L, NOW);
    }

    @Test
    void registerRejectsDuplicateLoginName() {
        when(authMapper.findByLoginName("reader")).thenReturn(enabledUser());

        assertThatThrownBy(() -> authService.register("reader", "Reader", "1234"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("LOGIN_NAME_ALREADY_EXISTS");
                });
    }

    @Test
    void loginUpdatesLastLoginTime() {
        AuthUser user = enabledUser();
        when(authMapper.findByLoginName("reader")).thenReturn(user);
        when(passwordEncoder.matches("1234", "hashed-pin")).thenReturn(true);

        AuthService.AuthenticatedUser result = authService.login("READER", "1234");

        assertThat(result.userId()).isEqualTo(11L);
        verify(authMapper).updateLastLoginAt(11L, NOW);
    }

    @Test
    void loginUsesGenericErrorForWrongPin() {
        AuthUser user = enabledUser();
        when(authMapper.findByLoginName("reader")).thenReturn(user);
        when(passwordEncoder.matches("9999", "hashed-pin")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("reader", "9999"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception.getCode()).isEqualTo("INVALID_CREDENTIALS");
                });
    }

    @Test
    void changePinVerifiesCurrentPinAndStoresNewHash() {
        AuthUser user = enabledUser();
        when(authMapper.findById(11L)).thenReturn(user);
        when(passwordEncoder.matches("1234", "hashed-pin")).thenReturn(true);
        when(passwordEncoder.matches("5678", "hashed-pin")).thenReturn(false);
        when(passwordEncoder.encode("5678")).thenReturn("new-hashed-pin");
        when(authMapper.updatePinHash(11L, "new-hashed-pin")).thenReturn(1);

        authService.changePin(11L, "1234", "5678");

        verify(authMapper).updatePinHash(11L, "new-hashed-pin");
    }

    @Test
    void changePinRejectsIncorrectCurrentPin() {
        when(authMapper.findById(11L)).thenReturn(enabledUser());
        when(passwordEncoder.matches("9999", "hashed-pin")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePin(11L, "9999", "5678"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getCode()).isEqualTo("CURRENT_PIN_INCORRECT");
                });
    }

    private AuthUser enabledUser() {
        AuthUser user = new AuthUser();
        user.setId(11L);
        user.setLoginName("reader");
        user.setDisplayName("Reader");
        user.setPinHash("hashed-pin");
        user.setAppRole("USER");
        user.setEnabled(true);
        return user;
    }
}
