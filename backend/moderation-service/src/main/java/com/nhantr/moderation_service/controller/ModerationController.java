package com.nhantr.moderation_service.controller;

import com.nhantr.common.security.CurrentUser;
import com.nhantr.moderation_service.service.AdminAuthService;
import com.nhantr.moderation_service.service.ModerationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class ModerationController {
    private final ModerationService moderation;
    private final AdminAuthService adminAuth;

    public ModerationController(ModerationService moderation, AdminAuthService adminAuth) {
        this.moderation = moderation;
        this.adminAuth = adminAuth;
    }

    @PostMapping("/api/v1/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ModerationService.ReportResponse report(
            @AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody ModerationService.CreateReportRequest request
    ) {
        return moderation.create(user.id(), request);
    }

    @PostMapping("/api/v1/admin/auth/login")
    public AdminAuthService.AdminAuthResponse adminLogin(
            @Valid @RequestBody AdminAuthService.AdminLoginRequest request
    ) {
        return adminAuth.login(request);
    }

    @GetMapping("/api/v1/admin/reports")
    @PreAuthorize("hasAnyRole('MODERATOR','SUPER_ADMIN')")
    public List<ModerationService.ReportResponse> reports() {
        return moderation.list();
    }

    @GetMapping("/api/v1/admin/reports/{id}")
    @PreAuthorize("hasAnyRole('MODERATOR','SUPER_ADMIN')")
    public ModerationService.ReportResponse report(@PathVariable Long id) {
        return moderation.get(id);
    }

    @PostMapping("/api/v1/admin/reports/{id}/resolve")
    @PreAuthorize("hasAnyRole('MODERATOR','SUPER_ADMIN')")
    public ModerationService.ReportResponse resolve(
            @AuthenticationPrincipal CurrentUser admin,
            @PathVariable Long id,
            @Valid @RequestBody ModerationService.ResolveReportRequest request
    ) {
        return moderation.resolve(admin.id(), id, request);
    }

    @PostMapping("/api/v1/admin/reports/{id}/reject")
    @PreAuthorize("hasAnyRole('MODERATOR','SUPER_ADMIN')")
    public ModerationService.ReportResponse reject(
            @AuthenticationPrincipal CurrentUser admin,
            @PathVariable Long id,
            @Valid @RequestBody ModerationService.RejectReportRequest request
    ) {
        return moderation.reject(admin.id(), id, request);
    }
}
