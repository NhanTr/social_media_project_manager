package com.nhantr.identity_service.controller;

import com.nhantr.common.security.CurrentUser;
import com.nhantr.identity_service.service.TermsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/terms")
public class TermsController {
    private final TermsService service;

    public TermsController(TermsService service) {
        this.service = service;
    }

    @GetMapping("/latest")
    public TermsService.TermsResponse latest() {
        return service.latest();
    }

    @PostMapping("/accept")
    public TermsService.AcceptanceResponse accept(
            @AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody AcceptTermsRequest request
    ) {
        return service.accept(user.id(), request.termsVersionId());
    }

    public record AcceptTermsRequest(@NotNull Long termsVersionId) {}
}
