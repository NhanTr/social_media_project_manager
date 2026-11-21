package com.nhantr.ai_service.provider;

import org.springframework.stereotype.Component;

@Component
public class DeterministicMockAiProvider implements AiProvider {
    @Override
    public AiGenerationResult generate(AiGenerationContext context) {
        String prompt = context.stylePrompt() != null ? context.stylePrompt().toLowerCase() : "";

        if (prompt.contains("[fail]")) {
            return AiGenerationResult.failure("Mock provider failure requested by prompt.");
        }

        if (prompt.contains("[timeout]")) {
            return AiGenerationResult.failure("AI generation job timed out after 30 seconds.");
        }

        String mediaUrl;
        if (context.originalMediaUrl() == null || context.originalMediaUrl().isBlank()) {
            mediaUrl = "https://picsum.photos/seed/loccoc-" + context.generationId() + "/1024/1024";
        } else {
            String separator = context.originalMediaUrl().contains("?") ? "&" : "?";
            mediaUrl = context.originalMediaUrl() + separator + "aiGen=" + context.generationId();
        }

        String widgetThumbnailUrl = "https://picsum.photos/seed/loccoc-" + context.generationId() + "-widget/512/512";

        return AiGenerationResult.success(mediaUrl, widgetThumbnailUrl);
    }
}
