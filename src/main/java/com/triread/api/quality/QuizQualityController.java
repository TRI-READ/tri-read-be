package com.triread.api.quality;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/quiz-quality")
public class QuizQualityController {
    private final QuizQualityService service;

    public QuizQualityController(QuizQualityService service) {
        this.service = service;
    }

    @GetMapping
    public QuizQualityResponse.QualityPage list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword
    ) {
        return service.getQualityPage(page, size, status, keyword);
    }
}
