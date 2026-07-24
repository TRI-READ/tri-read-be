package com.triread.api.operations;

import com.triread.api.audit.AdminAuditService;
import com.triread.api.auth.AuthPrincipal;
import com.triread.api.notification.DiscordNotificationService;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/operations")
public class AdminOperationsController {
    private final OperationsService service;
    private final DiscordNotificationService notificationService;
    private final AdminAuditService auditService;

    public AdminOperationsController(OperationsService service,
                                     DiscordNotificationService notificationService,
                                     AdminAuditService auditService) {
        this.service = service;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    @GetMapping("/summary")
    public OperationsData.Summary summary() {
        return service.summary();
    }

    @GetMapping("/notifications")
    public DiscordNotificationService.NotificationStatus notificationStatus() {
        return notificationService.status();
    }

    @PostMapping("/notifications/test")
    public DiscordNotificationService.SendResult testNotification(
            @AuthenticationPrincipal AuthPrincipal principal) {
        DiscordNotificationService.SendResult result =
                notificationService.sendTest(principal.loginName());
        auditService.record(principal.userId(), "DISCORD_NOTIFICATION_TESTED",
                "OPERATIONS", null, Map.of("delivered", result.delivered()));
        return result;
    }
}
