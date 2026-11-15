package com.nhantr.subscription_service;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionStateTests {
    private final Instant now = Instant.parse("2026-11-15T00:00:00Z");
    @Test void freeHasNoAiAndShortFreshness() {
        var state = SubscriptionState.free(42L);
        assertThat(state.snapshot(now).tier()).isEqualTo("FREE");
        assertThat(state.snapshot(now).entitlements().aiGeneration()).isFalse();
        assertThat(state.snapshot(now).validUntil()).isEqualTo(now.plusSeconds(60));
    }
    @Test void cancelRetainsGrantOnlyUntilEffectivePeriodEnd() {
        var state = SubscriptionState.free(42L);
        state.grant(now.plusSeconds(30), now);
        state.cancel(true, now);
        assertThat(state.snapshot(now).entitlements().aiGeneration()).isTrue();
        assertThat(state.snapshot(now).validUntil()).isEqualTo(now.plusSeconds(30));
        assertThat(state.snapshot(now.plusSeconds(30)).tier()).isEqualTo("FREE");
        assertThat(state.snapshot(now.plusSeconds(30)).status()).isEqualTo("CANCELED");
        assertThat(state.snapshot(now.plusSeconds(30)).entitlements().aiGeneration()).isFalse();
    }
    @Test void failureDoesNotDeactivateAnExistingPaidAccount() {
        var state = SubscriptionState.free(42L);
        state.grant(now.plusSeconds(3600), now);
        state.payment("new-session", "FAILED");
        assertThat(state.snapshot(now).entitlements().aiGeneration()).isTrue();
        assertThat(state.snapshot(now).paymentStatus()).isEqualTo("FAILED");
    }
    @Test void expiryAndRefundRevokeTheGrant() {
        var state = SubscriptionState.free(42L);
        state.grant(now.plusSeconds(3600), now);
        assertThat(state.snapshot(now.plusSeconds(3600)).entitlements().aiGeneration()).isFalse();
        state.revoke(now);
        assertThat(state.snapshot(now).tier()).isEqualTo("FREE");
    }
}
