package com.nhantr.ai_service.controller;

import com.nhantr.ai_service.service.AiGenerationService;
import com.nhantr.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/generations")
public class AiGenerationController {
    private final AiGenerationService service;

    public AiGenerationController(AiGenerationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AiGenerationService.GenerationResponse create(
            @AuthenticationPrincipal CurrentUser user,
            @RequestHeader(value = "X-Client-Request-Id", required = false) String clientRequestId,
            @Valid @RequestBody AiGenerationService.CreateGenerationRequest request
    ) {
        return service.create(user.id(), request, clientRequestId);
    }

    @GetMapping("/{id}")
    public AiGenerationService.GenerationResponse get(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id
    ) {
        return service.get(user.id(), id);
    }

    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AiGenerationService.GenerationResponse retry(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id
    ) {
        return service.retry(user.id(), id);
    }
}
