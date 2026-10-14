package com.nhantr.ai_service.controller;

import com.nhantr.ai_service.service.AiGenerationService;
import com.nhantr.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
            @Valid @RequestBody AiGenerationService.CreateGenerationRequest request
    ) {
        return service.create(user.id(), request);
    }

    @GetMapping("/{id}")
    public AiGenerationService.GenerationResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return service.get(user.id(), id);
    }

    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AiGenerationService.GenerationResponse retry(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return service.retry(user.id(), id);
    }
}
