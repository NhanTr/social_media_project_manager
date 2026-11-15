package com.nhantr.subscription_service;
import com.nhantr.common.api.ApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
@RestController
@ConditionalOnProperty(name="app.payment.sandbox-enabled",havingValue="true")
public class SandboxCheckoutController {
    private final SubscriptionService service;
    private final SandboxPaymentProvider provider;
    private final Clock clock;
    private final ObjectMapper json;
    private final SubscriptionController webhooks;
    public SandboxCheckoutController(SubscriptionService service,SandboxPaymentProvider provider,Clock clock,ObjectMapper json,SubscriptionController webhooks) {
        this.service=service;this.provider=provider;this.clock=clock;this.json=json;this.webhooks=webhooks;
    }
    @GetMapping(value="/api/v1/payments/sandbox/checkouts/{sessionId}",produces=MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> sandbox(@PathVariable String sessionId) {
        UUID.fromString(sessionId);
        var checkout=service.sandboxCheckout(sessionId);
        if(!checkout.expiresAt().isAfter(clock.instant())) return ResponseEntity.status(410).body("Sandbox checkout expired. Refresh in the app.");
        String html="<!doctype html><html lang='vi'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
                +"<title>LocCoc Payment Sandbox</title><body><h1>LocCoc Plus · Sandbox</h1><p>Mô phỏng thanh toán. Không thu tiền thật.</p>"
                +"<form method='post' action='"+sessionId+"/complete'><button name='outcome' value='SUCCEEDED'>Mô phỏng thành công</button> "
                +"<button name='outcome' value='FAILED'>Mô phỏng thất bại</button></form></body></html>";
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Content-Security-Policy","default-src 'none'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'")
                .header("Referrer-Policy","no-referrer").body(html);
    }
    @PostMapping(value="/api/v1/payments/sandbox/checkouts/{sessionId}/complete",produces=MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> completeSandbox(@PathVariable String sessionId,@RequestParam String outcome) {
        UUID.fromString(sessionId);
        if(!java.util.List.of("SUCCEEDED","FAILED").contains(outcome)) throw ApiException.badRequest("INVALID_SANDBOX_OUTCOME","Unknown sandbox outcome.");
        var checkout=service.sandboxCheckout(sessionId); Instant now=checkout.expiresAt().minusSeconds(1800).truncatedTo(ChronoUnit.MICROS);
        if(!checkout.expiresAt().isAfter(clock.instant())) return ResponseEntity.status(410).body("Sandbox checkout expired.");
        var event=new SubscriptionService.PaymentEvent("sandbox_"+sessionId.replace("-", "")+"_"+outcome,sessionId,outcome,now,
                "SUCCEEDED".equals(outcome)?now.plus(30,ChronoUnit.DAYS):null);
        String body=json.writeValueAsString(event);
        // This hosted mock is the sandbox provider. It signs its event and exercises the same verifier as incoming webhooks.
        webhooks.webhook(body,provider.sign(body,clock.instant()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Content-Security-Policy","default-src 'none'; frame-ancestors 'none'")
                .body("<!doctype html><html lang='vi'><meta charset='utf-8'><title>Sandbox result</title><h1>Đã gửi sự kiện sandbox</h1><p>Quay lại LocCoc và làm mới quyền lợi.</p></html>");
    }
}
