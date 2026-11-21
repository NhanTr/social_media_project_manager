package com.nhantr.ai_service.controller;

import com.nhantr.ai_service.service.AiGenerationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/ai/generations")
public class InternalAiGenerationController {
    private final AiGenerationService service;

    public InternalAiGenerationController(AiGenerationService service) {
        this.service = service;
    }

    @GetMapping("/{id}/verify")
    public AiGenerationService.VerifyResponse verify(
            @PathVariable Long id,
            @RequestParam Long userId
    ) {
        return service.verifyForPublication(userId, id);
    }
}
