package com.triread.api.auth;

import java.io.Serializable;

public record AuthPrincipal(
        long userId,
        String loginName,
        String displayName,
        String role
) implements Serializable {

    public static AuthPrincipal from(AuthService.AuthenticatedUser user) {
        return new AuthPrincipal(
                user.userId(),
                user.loginName(),
                user.displayName(),
                user.role()
        );
    }
}
