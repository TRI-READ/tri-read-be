package com.triread.api.prompt;

import com.triread.api.common.PageResponse;
import java.time.Instant;
import java.util.List;

public interface PromptTemplateService {
    PromptPage getVersions(String promptType, int page, int size);
    PromptVersion createVersion(long userId, String promptType, String content, String changeNote);
    PromptVersion activate(long userId, long promptTemplateId);
    ActivePrompts getActivePrompts();

    record PromptSnapshot(long promptTemplateId, String promptType, int versionNumber,
                          String content, String contentHash) {
        public String versionLabel() {
            return promptType.toLowerCase() + "-v" + versionNumber;
        }
    }

    record ActivePrompts(PromptSnapshot generation, PromptSnapshot validation) {
        public String versionLabel() {
            return "g" + generation.versionNumber() + "/v" + validation.versionNumber();
        }
    }

    record PromptVersion(long promptTemplateId, String promptType, int versionNumber,
                         String content, String contentHash, String changeNote,
                         Long createdByUserId, String createdByName, Instant createdAt,
                         String status, Instant lastActivatedAt) {
    }

    record Activation(long activationId, long promptTemplateId, int versionNumber,
                      Long activatedByUserId, String activatedByName, Instant activatedAt) {
    }

    record PromptPage(PageResponse<PromptVersion> page, PromptVersion active,
                      List<Activation> recentActivations) {
    }
}
