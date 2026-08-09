package com.triread.api.quality;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface QuizQualityMapper {
    List<QuizQualityData.QuestionRow> findQuestions(
            @Param("status") String status,
            @Param("keyword") String keyword,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countQuestions(@Param("status") String status, @Param("keyword") String keyword);

    List<QuizQualityData.StatusCount> countByStatus();

    List<QuizQualityData.OptionRow> findOptions(@Param("questionIds") List<Long> questionIds);
}
