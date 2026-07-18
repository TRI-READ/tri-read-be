package com.triread.api.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {
    private String bootstrapAdminLoginName = "";

    public String getBootstrapAdminLoginName() {
        return bootstrapAdminLoginName;
    }

    public void setBootstrapAdminLoginName(String bootstrapAdminLoginName) {
        this.bootstrapAdminLoginName = bootstrapAdminLoginName;
    }
}
