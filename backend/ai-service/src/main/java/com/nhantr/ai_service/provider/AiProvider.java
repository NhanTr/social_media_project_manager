package com.nhantr.ai_service.provider;

import com.nhantr.ai_service.entity.AiGeneration;

/** Provider implementations must use generation.id as their idempotency key. */
public interface AiProvider {
    String generate(AiGeneration generation) throws Exception;
}
