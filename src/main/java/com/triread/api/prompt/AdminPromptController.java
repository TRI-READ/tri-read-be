package com.triread.api.prompt;

import com.triread.api.auth.AuthPrincipal;
import com.triread.api.audit.AdminAuditService;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/prompts")
public class AdminPromptController {
    private final PromptTemplateService service;
    private final AdminAuditService auditService;

    public AdminPromptController(PromptTemplateService service, AdminAuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    @GetMapping
    public PromptTemplateService.PromptPage versions(
            @RequestParam(defaultValue = "GENERATION") String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size
    ) {
        return service.getVersions(type, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PromptTemplateService.PromptVersion create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreatePromptRequest request
    ) {
        PromptTemplateService.PromptVersion created = service.createVersion(principal.userId(), request.promptType(),
                request.content(), request.changeNote());
        auditService.record(principal.userId(), "PROMPT_VERSION_CREATED", "PROMPT",
                created.promptTemplateId(), Map.of("promptType", created.promptType(),
                        "version", created.versionNumber()));
        return created;
    }

    @PostMapping("/{promptTemplateId}/activate")
    public PromptTemplateService.PromptVersion activate(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable long promptTemplateId
    ) {
        PromptTemplateService.PromptVersion activated = service.activate(principal.userId(), promptTemplateId);
        auditService.record(principal.userId(), "PROMPT_VERSION_ACTIVATED", "PROMPT",
                promptTemplateId, Map.of("promptType", activated.promptType(),
                        "version", activated.versionNumber()));
        return activated;
    }

    public record CreatePromptRequest(
            @NotBlank String promptType,
            @NotBlank @Size(max = 20_000) String content,
            @NotBlank @Size(max = 300) String changeNote
    ) {
    }
}
