package com.triread.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;

class SessionInvalidationServiceTest {

    @Test
    void invalidateUserExpiresOnlyTheRequestedUsersSessions() {
        SessionRegistry sessionRegistry = mock(SessionRegistry.class);
        SessionInvalidationService service =
                new SessionInvalidationService(sessionRegistry);
        AuthPrincipal target =
                new AuthPrincipal(1L, "reader", "Reader", "USER");
        AuthPrincipal other =
                new AuthPrincipal(2L, "other", "Other", "USER");
        SessionInformation firstSession = mock(SessionInformation.class);
        SessionInformation secondSession = mock(SessionInformation.class);

        when(sessionRegistry.getAllPrincipals())
                .thenReturn(List.of(target, other, "system"));
        when(sessionRegistry.getAllSessions(target, false))
                .thenReturn(List.of(firstSession, secondSession));

        int invalidated = service.invalidateUser(1L);

        assertThat(invalidated).isEqualTo(2);
        verify(firstSession).expireNow();
        verify(secondSession).expireNow();
        verify(sessionRegistry, never()).getAllSessions(other, false);
    }
}
