package com.triread.api.prompt;

import java.time.Instant;

public final class PromptTemplateData {
    private PromptTemplateData() {
    }

    public static final class PromptInsert {
        private Long id;
        private final String promptType;
        private final int versionNumber;
        private final String content;
        private final String contentHash;
        private final String changeNote;
        private final long createdByUserId;

        public PromptInsert(String promptType, int versionNumber, String content,
                            String contentHash, String changeNote, long createdByUserId) {
            this.promptType = promptType;
            this.versionNumber = versionNumber;
            this.content = content;
            this.contentHash = contentHash;
            this.changeNote = changeNote;
            this.createdByUserId = createdByUserId;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getPromptType() { return promptType; }
        public int getVersionNumber() { return versionNumber; }
        public String getContent() { return content; }
        public String getContentHash() { return contentHash; }
        public String getChangeNote() { return changeNote; }
        public long getCreatedByUserId() { return createdByUserId; }
    }

    public record PromptRow(long promptTemplateId, String promptType, int versionNumber,
                            String content, String contentHash, String changeNote,
                            Long createdByUserId, String createdByName, Instant createdAt,
                            String status, Instant lastActivatedAt) {
    }

    public record ActivationRow(long activationId, long promptTemplateId, int versionNumber,
                                Long activatedByUserId, String activatedByName,
                                Instant activatedAt) {
    }
}
