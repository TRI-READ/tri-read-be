package com.triread.api.auth;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AuthMapper authMapper;
    private final AuthProperties properties;

    public AdminBootstrap(AuthMapper authMapper, AuthProperties properties) {
        this.authMapper = authMapper;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String loginName = properties.getBootstrapAdminLoginName();
        if (loginName == null || loginName.isBlank()) return;

        String normalized = loginName.trim().toLowerCase(Locale.ROOT);
        int updated = authMapper.promoteBootstrapAdmin(normalized);
        if (updated == 1) {
            log.info("Bootstrap administrator role was granted to {}", normalized);
        } else if (authMapper.findByLoginName(normalized) == null) {
            log.warn("Bootstrap administrator account {} does not exist yet", normalized);
        }
    }
}
