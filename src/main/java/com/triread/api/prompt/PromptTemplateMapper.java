package com.triread.api.prompt;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PromptTemplateMapper {
    List<PromptTemplateData.PromptRow> findVersions(@Param("promptType") String promptType,
                                                     @Param("offset") int offset,
                                                     @Param("limit") int limit);
    long countVersions(@Param("promptType") String promptType);
    PromptTemplateData.PromptRow findVersion(@Param("promptTemplateId") long promptTemplateId);
    PromptTemplateData.PromptRow findActive(@Param("promptType") String promptType);
    String lockPromptType(@Param("promptType") String promptType);
    int nextVersion(@Param("promptType") String promptType);
    void insertTemplate(PromptTemplateData.PromptInsert prompt);
    void insertActivation(@Param("promptTemplateId") long promptTemplateId,
                          @Param("activatedByUserId") long activatedByUserId);
    List<PromptTemplateData.ActivationRow> findRecentActivations(
            @Param("promptType") String promptType,
            @Param("limit") int limit
    );
}
