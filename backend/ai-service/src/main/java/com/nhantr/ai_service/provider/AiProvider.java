package com.nhantr.ai_service.provider;

public interface AiProvider {
    AiGenerationResult generate(AiGenerationContext context);

    record AiGenerationContext(
            Long generationId,
            Long userId,
            String stylePrompt,
            String originalMediaUrl,
            String generationType
    ) {}

    record AiGenerationResult(
            boolean success,
            String generatedMediaUrl,
            String widgetThumbnailUrl,
            String errorMessage
    ) {
        public static AiGenerationResult success(String mediaUrl, String thumbnailUrl) {
            return new AiGenerationResult(true, mediaUrl, thumbnailUrl, null);
        }

        public static AiGenerationResult failure(String errorMessage) {
            return new AiGenerationResult(false, null, null, errorMessage);
        }
    }
}
