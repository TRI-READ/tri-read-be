package com.triread.api.audit;

import com.triread.api.common.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AdminAuditController {
    private final AdminAuditService service;
    public AdminAuditController(AdminAuditService service) { this.service = service; }

    @GetMapping
    public PageResponse<AdminAuditData.AuditRow> logs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return service.getLogs(page, size);
    }
}
