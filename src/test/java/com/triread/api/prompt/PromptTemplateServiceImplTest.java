package com.triread.api.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PromptTemplateServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-07-19T00:00:00Z");

    @Mock PromptTemplateMapper mapper;
    private PromptTemplateServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PromptTemplateServiceImpl(mapper);
    }

    @Test
    void createsNormalizedImmutableVersionAfterAcquiringTypeLock() {
        when(mapper.nextVersion("GENERATION")).thenReturn(3);
        when(mapper.findVersion(31L)).thenReturn(row(
                31L, "GENERATION", 3, "new prompt", "ACTIVE", null));
        org.mockito.Mockito.doAnswer(invocation -> {
            PromptTemplateData.PromptInsert insert = invocation.getArgument(0);
            insert.setId(31L);
            return null;
        }).when(mapper).insertTemplate(any());

        PromptTemplateService.PromptVersion result = service.createVersion(
                7L, " generation ", "  new prompt  ", "  표현 명확화  ");

        ArgumentCaptor<PromptTemplateData.PromptInsert> captor =
                ArgumentCaptor.forClass(PromptTemplateData.PromptInsert.class);
        verify(mapper).lockPromptType("GENERATION");
        verify(mapper).insertTemplate(captor.capture());
        assertThat(captor.getValue().getVersionNumber()).isEqualTo(3);
        assertThat(captor.getValue().getContent()).isEqualTo("new prompt");
        assertThat(captor.getValue().getChangeNote()).isEqualTo("표현 명확화");
        assertThat(captor.getValue().getContentHash()).hasSize(64);
        assertThat(result.promptTemplateId()).isEqualTo(31L);
    }

    @Test
    void activatesDraftVersionByAppendingActivationHistory() {
        PromptTemplateData.PromptRow draft = row(
                31L, "VALIDATION", 4, "validate", "DRAFT", null);
        PromptTemplateData.PromptRow active = row(
                31L, "VALIDATION", 4, "validate", "ACTIVE", NOW);
        when(mapper.findVersion(31L)).thenReturn(draft, active);

        PromptTemplateService.PromptVersion result = service.activate(7L, 31L);

        verify(mapper).insertActivation(31L, 7L);
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.lastActivatedAt()).isEqualTo(NOW);
    }

    @Test
    void doesNotDuplicateActivationForAlreadyActiveVersion() {
        when(mapper.findVersion(31L)).thenReturn(row(
                31L, "GENERATION", 3, "generate", "ACTIVE", NOW));

        PromptTemplateService.PromptVersion result = service.activate(7L, 31L);

        verify(mapper, never()).insertActivation(31L, 7L);
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    void returnsGenerationAndValidationPromptSnapshotsTogether() {
        when(mapper.findActive("GENERATION")).thenReturn(row(
                11L, "GENERATION", 3, "generate", "ACTIVE", NOW));
        when(mapper.findActive("VALIDATION")).thenReturn(row(
                12L, "VALIDATION", 5, "validate", "ACTIVE", NOW));

        PromptTemplateService.ActivePrompts result = service.getActivePrompts();

        assertThat(result.generation().promptTemplateId()).isEqualTo(11L);
        assertThat(result.validation().promptTemplateId()).isEqualTo(12L);
        assertThat(result.versionLabel()).isEqualTo("g3/v5");
    }

    private PromptTemplateData.PromptRow row(long id, String type, int version,
                                              String content, String status,
                                              Instant lastActivatedAt) {
        return new PromptTemplateData.PromptRow(
                id, type, version, content, "a".repeat(64), "change", 7L,
                "관리자", NOW, status, lastActivatedAt);
    }
}
