package com.triread.api.operations;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/operations")
public class AdminOperationsController {
    private final OperationsService service;
    public AdminOperationsController(OperationsService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public OperationsData.Summary summary() {
        return service.summary();
    }
}
