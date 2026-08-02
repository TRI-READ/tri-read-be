package com.triread.api.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.triread.api.auth.AuthPrincipal;
import com.triread.api.audit.AdminAuditService;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdminPromptControllerTest {

    private final PromptTemplateService service = mock(PromptTemplateService.class);
    private final AdminAuditService auditService = mock(AdminAuditService.class);
    private final AdminPromptController controller =
            new AdminPromptController(service, auditService);
    private final AuthPrincipal admin =
            new AuthPrincipal(1L, "admin", "Admin", "ADMIN");

    @Test
    void createRecordsPromptVersionAuditLog() {
        PromptTemplateService.PromptVersion created = version(31L, 3, "DRAFT");
        when(service.createVersion(1L, "GENERATION", "new prompt", "change"))
                .thenReturn(created);

        PromptTemplateService.PromptVersion result = controller.create(
                admin,
                new AdminPromptController.CreatePromptRequest(
                        "GENERATION", "new prompt", "change")
        );

        assertThat(result).isEqualTo(created);
        verify(auditService).record(
                1L,
                "PROMPT_VERSION_CREATED",
                "PROMPT",
                31L,
                Map.of("promptType", "GENERATION", "version", 3)
        );
    }

    @Test
    void activateRecordsPromptVersionAuditLog() {
        PromptTemplateService.PromptVersion activated = version(31L, 3, "ACTIVE");
        when(service.activate(1L, 31L)).thenReturn(activated);

        PromptTemplateService.PromptVersion result = controller.activate(admin, 31L);

        assertThat(result).isEqualTo(activated);
        verify(auditService).record(
                1L,
                "PROMPT_VERSION_ACTIVATED",
                "PROMPT",
                31L,
                Map.of("promptType", "GENERATION", "version", 3)
        );
    }

    private PromptTemplateService.PromptVersion version(long id, int version, String status) {
        return new PromptTemplateService.PromptVersion(
                id,
                "GENERATION",
                version,
                "prompt",
                "a".repeat(64),
                "change",
                1L,
                "Admin",
                Instant.EPOCH,
                status,
                null
        );
    }
}
