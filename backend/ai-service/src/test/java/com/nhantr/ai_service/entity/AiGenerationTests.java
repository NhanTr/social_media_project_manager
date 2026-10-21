package com.nhantr.ai_service.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiGenerationTests {
    @Test
    void supportsExpectedStateTransitions() {
        AiGeneration generation = new AiGeneration(1L, "image.jpg", "anime", AiGeneration.Type.IMAGE_TO_IMAGE);
        generation.processing();
        assertThat(generation.getStatus()).isEqualTo(AiGeneration.Status.PROCESSING);
        generation.success("generated.jpg");
        assertThat(generation.getStatus()).isEqualTo(AiGeneration.Status.SUCCESS);
        assertThat(generation.getGeneratedMediaUrl()).isEqualTo("generated.jpg");
    }
}
