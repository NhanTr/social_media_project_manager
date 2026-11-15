package com.nhantr.subscription_service;

import java.time.Instant;

/** Account aggregate. A billed period is authoritative; expired grants are never cached as valid. */
public class SubscriptionState {
    final Long userId;
    String tier = "FREE";
    String status = "ACTIVE";
    String paymentStatus = "NONE";
    String paymentSessionId;
    String grantSessionId;
    boolean cancelAtPeriodEnd;
    Instant currentPeriodEnd;
    Instant effectiveAt;
    public SubscriptionState(Long userId) { this.userId = userId; }
    public static SubscriptionState free(Long userId) { return new SubscriptionState(userId); }
    public void grant(Instant end, Instant now) {
        tier = "PLUS"; status = "ACTIVE"; currentPeriodEnd = end;
        cancelAtPeriodEnd = false; effectiveAt = now;
    }
    public void revoke(Instant now) {
        tier = "FREE"; status = "CANCELED"; currentPeriodEnd = now;
        cancelAtPeriodEnd = false; effectiveAt = now;
    }
    public void cancel(boolean cancel, Instant now) { cancelAtPeriodEnd = cancel; effectiveAt = now; }
    public void payment(String session, String state) { paymentSessionId = session; paymentStatus = state; }
    public Snapshot snapshot(Instant now) {
        boolean plus = "PLUS".equals(tier) && currentPeriodEnd != null && currentPeriodEnd.isAfter(now);
        String currentStatus = !plus && "PLUS".equals(tier) ? (cancelAtPeriodEnd ? "CANCELED" : "EXPIRED") : status;
        Instant validUntil = now.plusSeconds(60);
        if (plus && currentPeriodEnd.isBefore(validUntil)) validUntil = currentPeriodEnd;
        return new Snapshot(plus ? "PLUS" : "FREE", currentStatus, paymentStatus, paymentSessionId,
                cancelAtPeriodEnd, currentPeriodEnd, validUntil, new Entitlements(plus), effectiveAt);
    }
    public record Entitlements(boolean aiGeneration) {}
    public record Snapshot(String tier, String status, String paymentStatus, String paymentSessionId,
            boolean cancelAtPeriodEnd, Instant currentPeriodEnd, Instant validUntil,
            Entitlements entitlements, Instant effectiveAt) {}
}
