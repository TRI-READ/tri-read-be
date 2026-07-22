package com.triread.api.auth;

import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;

@Service
public class SessionInvalidationService {

    private final SessionRegistry sessionRegistry;

    public SessionInvalidationService(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    public int invalidateUser(long userId) {
        int invalidated = 0;
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof AuthPrincipal authPrincipal && authPrincipal.userId() == userId) {
                var sessions = sessionRegistry.getAllSessions(principal, false);
                sessions.forEach(session -> session.expireNow());
                invalidated += sessions.size();
            }
        }
        return invalidated;
    }
}
