package com.triread.api.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import com.triread.api.common.PageResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AdminUserService.UserSummary> users(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return service.getUsers(page, size);
    }

    @PatchMapping("/{userId}/role")
    public AdminUserService.UserSummary updateRole(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Positive long userId,
            @Valid @RequestBody UpdateRoleRequest request
    ) {
        return service.updateRole(principal.userId(), userId, request.role());
    }

    public record UpdateRoleRequest(@NotBlank String role) {}
}
