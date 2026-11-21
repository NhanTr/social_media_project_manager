package com.nhantr.ai_service.entitlement;

public interface SubscriptionEntitlementClient {
    EntitlementDecision checkAiEntitlement(Long userId, String generationType);

    MockChargeResult reserveCharge(Long userId, Long generationId);

    void commitCharge(String mockChargeId);

    void releaseCharge(String mockChargeId);

    record EntitlementDecision(boolean allowed, String tier, String reason) {
        public static EntitlementDecision allow(String tier) {
            return new EntitlementDecision(true, tier, null);
        }

        public static EntitlementDecision deny(String tier, String reason) {
            return new EntitlementDecision(false, tier, reason);
        }
    }

    record MockChargeResult(String chargeId, boolean success, String message) {
        public static MockChargeResult success(String chargeId) {
            return new MockChargeResult(chargeId, true, null);
        }

        public static MockChargeResult failure(String message) {
            return new MockChargeResult(null, false, message);
        }
    }
}
