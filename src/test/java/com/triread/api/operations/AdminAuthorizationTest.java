package com.triread.api.operations;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.triread.api.audit.AdminAuditService;
import com.triread.api.config.SecurityConfig;
import com.triread.api.notification.DiscordNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@SpringBootTest(classes = {AdminOperationsController.class, SecurityConfig.class})
class AdminAuthorizationTest {
    @Autowired WebApplicationContext context;
    @MockitoBean OperationsService operationsService;
    @MockitoBean DiscordNotificationService notificationService;
    @MockitoBean AdminAuditService auditService;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void anonymousUserCannotOpenAdminApi() throws Exception {
        mockMvc.perform(get("/api/admin/operations/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void normalUserCannotOpenAdminApi() throws Exception {
        mockMvc.perform(get("/api/admin/operations/summary")
                        .with(user("reader").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void adminCanOpenAdminApi() throws Exception {
        when(operationsService.summary()).thenReturn(null);

        mockMvc.perform(get("/api/admin/operations/summary")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }
}
