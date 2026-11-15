package com.nhantr.subscription_service;

import com.nhantr.common.api.ApiException;
import com.nhantr.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@RestController
public class SubscriptionController {
    private final SubscriptionService service;
    private final SandboxPaymentProvider provider;
    private final Clock clock;
    private final ObjectMapper json;
    public SubscriptionController(SubscriptionService service,SandboxPaymentProvider provider,Clock clock,ObjectMapper json) {
        this.service=service;this.provider=provider;this.clock=clock;this.json=json;
    }
    @GetMapping("/api/v1/subscriptions/plans")
    public ResponseEntity<?> plans(@AuthenticationPrincipal CurrentUser principal) {
        user(principal); return noStore(Map.of("items",service.plans()));
    }
    @GetMapping("/api/v1/subscriptions/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal CurrentUser principal) { return noStore(service.snapshot(user(principal))); }
    @PostMapping("/api/v1/subscriptions/me/cancel")
    public ResponseEntity<?> cancel(@AuthenticationPrincipal CurrentUser principal,@Valid @RequestBody CancelRequest request) {
        service.cancel(user(principal),request.cancelAtPeriodEnd()); return noStore(service.snapshot(principal.id()));
    }
    @PostMapping("/api/v1/subscriptions/checkout")
    public ResponseEntity<?> checkout(@AuthenticationPrincipal CurrentUser principal,@Valid @RequestBody CheckoutRequest request) {
        return noStore(service.checkout(user(principal),request.tier(),request.clientRequestId()));
    }
    @PostMapping("/api/v1/admin/subscriptions/{userId}/override")
    public ResponseEntity<?> override(@AuthenticationPrincipal CurrentUser principal,@PathVariable Long userId,@Valid @RequestBody OverrideRequest request) {
        if(principal==null || !principal.isAdmin()) throw ApiException.forbidden("ADMIN_REQUIRED","Admin role is required.");
        service.override(principal.id(),userId,request.tier(),request.currentPeriodEnd(),request.note());
        return noStore(service.snapshot(userId));
    }
    @GetMapping("/internal/v1/subscriptions/{userId}/entitlements")
    public ResponseEntity<?> entitlements(@AuthenticationPrincipal CurrentUser principal,@PathVariable Long userId) {
        if(principal==null || !"SERVICE".equals(principal.role())) throw ApiException.forbidden("SERVICE_REQUIRED","Internal service authentication is required.");
        return noStore(service.entitlements(userId));
    }
    @PostMapping("/api/v1/payments/webhook")
    public ResponseEntity<?> webhook(@RequestBody String body,@RequestHeader(value="X-Payment-Signature",required=false) String signature) {
        if(body.length()>16384) throw ApiException.badRequest("INVALID_PAYMENT_EVENT","Payment body is too large.");
        provider.verify(body,signature,clock.instant());
        SubscriptionService.PaymentEvent event;
        try { event=json.readValue(body,SubscriptionService.PaymentEvent.class); }
        catch(RuntimeException e) { throw ApiException.badRequest("INVALID_PAYMENT_EVENT","Payment event JSON is invalid."); }
        return noStore(Map.of("applied",service.applyEvent(event)));
    }
    private Long user(CurrentUser p) {
        if(p==null || !"USER".equals(p.role()) || p.id()==null || p.id()<=0 || !p.termsAccepted())
            throw ApiException.forbidden("USER_TERMS_REQUIRED","An authenticated user with accepted terms is required.");
        return p.id();
    }
    private <T> ResponseEntity<T> noStore(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
    public record CheckoutRequest(@NotNull String tier,@NotNull UUID clientRequestId) {}
    public record CancelRequest(@NotNull Boolean cancelAtPeriodEnd) {}
    public record OverrideRequest(@NotNull String tier,Instant currentPeriodEnd,@NotNull @Size(min=1,max=1000) String note) {}
}
