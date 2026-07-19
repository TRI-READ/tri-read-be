package com.triread.api.prompt;

import com.triread.api.common.ApiException;
import com.triread.api.common.PageResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromptTemplateServiceImpl implements PromptTemplateService {
    private static final Set<String> PROMPT_TYPES = Set.of("GENERATION", "VALIDATION");
    private static final int MAX_CONTENT_LENGTH = 20_000;
    private static final int MAX_CHANGE_NOTE_LENGTH = 300;

    private final PromptTemplateMapper mapper;

    public PromptTemplateServiceImpl(PromptTemplateMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PromptPage getVersions(String requestedType, int requestedPage, int requestedSize) {
        String promptType = normalizeType(requestedType);
        int page = PageResponse.page(requestedPage);
        int size = PageResponse.size(requestedSize);
        long total = mapper.countVersions(promptType);
        List<PromptVersion> versions = mapper.findVersions(promptType, page * size, size).stream()
                .map(this::toVersion)
                .toList();
        PromptTemplateData.PromptRow active = mapper.findActive(promptType);
        return new PromptPage(
                PageResponse.of(versions, page, size, total),
                active == null ? null : toVersion(active),
                mapper.findRecentActivations(promptType, 20).stream().map(this::toActivation).toList()
        );
    }

    @Override
    @Transactional
    public PromptVersion createVersion(long userId, String requestedType,
                                       String requestedContent, String requestedChangeNote) {
        String promptType = normalizeType(requestedType);
        String content = normalizeContent(requestedContent);
        String changeNote = normalizeChangeNote(requestedChangeNote);
        mapper.lockPromptType(promptType);
        PromptTemplateData.PromptInsert insert = new PromptTemplateData.PromptInsert(
                promptType, mapper.nextVersion(promptType), content, hash(content), changeNote, userId);
        mapper.insertTemplate(insert);
        return toVersion(requireVersion(insert.getId()));
    }

    @Override
    @Transactional
    public PromptVersion activate(long userId, long promptTemplateId) {
        PromptTemplateData.PromptRow prompt = requireVersion(promptTemplateId);
        if (!"ACTIVE".equals(prompt.status())) {
            mapper.insertActivation(promptTemplateId, userId);
        }
        return toVersion(requireVersion(promptTemplateId));
    }

    @Override
    @Transactional(readOnly = true)
    public ActivePrompts getActivePrompts() {
        return new ActivePrompts(active("GENERATION"), active("VALIDATION"));
    }

    private PromptSnapshot active(String promptType) {
        PromptTemplateData.PromptRow row = mapper.findActive(promptType);
        if (row == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ACTIVE_PROMPT_MISSING",
                    "An active " + promptType.toLowerCase(Locale.ROOT) + " prompt is required.");
        }
        return new PromptSnapshot(row.promptTemplateId(), row.promptType(), row.versionNumber(),
                row.content(), row.contentHash());
    }

    private PromptTemplateData.PromptRow requireVersion(long promptTemplateId) {
        PromptTemplateData.PromptRow row = mapper.findVersion(promptTemplateId);
        if (row == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROMPT_VERSION_NOT_FOUND",
                    "The prompt version was not found.");
        }
        return row;
    }

    private String normalizeType(String requestedType) {
        String promptType = requestedType == null ? "" : requestedType.trim().toUpperCase(Locale.ROOT);
        if (!PROMPT_TYPES.contains(promptType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROMPT_TYPE_INVALID",
                    "Prompt type must be GENERATION or VALIDATION.");
        }
        return promptType;
    }

    private String normalizeContent(String requestedContent) {
        String content = requestedContent == null ? "" : requestedContent.trim();
        if (content.isEmpty() || content.length() > MAX_CONTENT_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROMPT_CONTENT_INVALID",
                    "Prompt content must contain between 1 and 20000 characters.");
        }
        return content;
    }

    private String normalizeChangeNote(String requestedChangeNote) {
        String changeNote = requestedChangeNote == null ? "" : requestedChangeNote.trim();
        if (changeNote.isEmpty() || changeNote.length() > MAX_CHANGE_NOTE_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROMPT_CHANGE_NOTE_INVALID",
                    "A change note between 1 and 300 characters is required.");
        }
        return changeNote;
    }

    private String hash(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private PromptVersion toVersion(PromptTemplateData.PromptRow row) {
        return new PromptVersion(row.promptTemplateId(), row.promptType(), row.versionNumber(),
                row.content(), row.contentHash(), row.changeNote(), row.createdByUserId(),
                row.createdByName(), row.createdAt(), row.status(), row.lastActivatedAt());
    }

    private Activation toActivation(PromptTemplateData.ActivationRow row) {
        return new Activation(row.activationId(), row.promptTemplateId(), row.versionNumber(),
                row.activatedByUserId(), row.activatedByName(), row.activatedAt());
    }
}
