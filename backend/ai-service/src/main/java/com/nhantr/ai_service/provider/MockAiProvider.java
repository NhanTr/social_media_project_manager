package com.nhantr.ai_service.provider;

import com.nhantr.ai_service.entity.AiGeneration;
import org.springframework.stereotype.Component;
import java.util.Locale;

/** Deterministic demo adapter. It never bills or calls an external AI provider. */
@Component
public class MockAiProvider implements AiProvider {
    @Override public String generate(AiGeneration generation) throws InterruptedException {
        String prompt = generation.getStylePrompt().toLowerCase(Locale.ROOT);
        if (prompt.contains("[fail]")) throw new IllegalStateException("Mock provider failure");
        if (prompt.contains("[timeout]")) Thread.sleep(60_000);
        String original = generation.getOriginalMediaUrl();
        return original == null ? "https://picsum.photos/seed/loccoc-" + generation.getId() + "/1024/1024"
                : original + (original.contains("?") ? "&" : "?") + "aiGeneration=" + generation.getId();
    }
}
