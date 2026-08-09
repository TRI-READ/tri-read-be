package com.triread.api.quality;

public interface QuizQualityService {
    QuizQualityResponse.QualityPage getQualityPage(int page, int size, String status, String keyword);
}
