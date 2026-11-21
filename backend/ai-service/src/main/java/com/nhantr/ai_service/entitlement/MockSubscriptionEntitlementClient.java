package com.nhantr.ai_service.entitlement;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MockSubscriptionEntitlementClient implements SubscriptionEntitlementClient {
    private final Map<Long, String> generationCharges = new ConcurrentHashMap<>();
    private final Map<String, Boolean> chargeStatuses = new ConcurrentHashMap<>();

    @Override
    public EntitlementDecision checkAiEntitlement(Long userId, String generationType) {
        if (userId != null && (userId == 999L || userId == 403L)) {
            return EntitlementDecision.deny("FREE", "User does not have an active subscription tier for AI generation.");
        }
        return EntitlementDecision.allow("PRO");
    }

    @Override
    public MockChargeResult reserveCharge(Long userId, Long generationId) {
        // Đảm bảo tính Idempotency: Mỗi generation ID chỉ phát sinh tối đa 1 charge ID duy nhất
        String existing = generationCharges.get(generationId);
        if (existing != null) {
            return MockChargeResult.success(existing);
        }

        String chargeId = "mock-chg-user-" + userId + "-gen-" + generationId;
        generationCharges.put(generationId, chargeId);
        chargeStatuses.put(chargeId, true);
        return MockChargeResult.success(chargeId);
    }

    @Override
    public void commitCharge(String mockChargeId) {
        if (mockChargeId != null) {
            chargeStatuses.put(mockChargeId, true);
        }
    }

    @Override
    public void releaseCharge(String mockChargeId) {
        if (mockChargeId != null) {
            chargeStatuses.put(mockChargeId, false);
        }
    }

    public boolean isChargeActive(String mockChargeId) {
        return Boolean.TRUE.equals(chargeStatuses.get(mockChargeId));
    }

    public int getChargeCountForGeneration(Long generationId) {
        return generationCharges.containsKey(generationId) ? 1 : 0;
    }
}
