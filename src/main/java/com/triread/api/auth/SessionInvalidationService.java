package com.triread.api.auth;

import java.util.List;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;

@Service
public class SessionInvalidationService {

    private final SessionRegistry sessionRegistry;

    public SessionInvalidationService(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    public void registerSession(String sessionId, AuthPrincipal principal) {
        sessionRegistry.registerNewSession(sessionId, principal);
    }

    public int invalidateUser(long userId) {
        int invalidated = 0;
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (!(principal instanceof AuthPrincipal)) {
                continue;
            }

            AuthPrincipal authPrincipal = (AuthPrincipal) principal;
            if (authPrincipal.userId() != userId) {
                continue;
            }

            List<SessionInformation> sessions =
                    sessionRegistry.getAllSessions(principal, false);
            for (SessionInformation session : sessions) {
                session.expireNow();
            }
            invalidated += sessions.size();
        }
        return invalidated;
    }
}
