package com.nhantr.subscription_service;
import com.nhantr.common.api.ApiException;
import com.nhantr.common.security.CurrentUser;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
class SubscriptionControllerTests {
    private final SubscriptionService service=mock(SubscriptionService.class);
    private final SandboxPaymentProvider provider=new SandboxPaymentProvider("https://sandbox.example/checkouts","test-only-webhook-key-at-least-32-bytes");
    private final SubscriptionController controller=new SubscriptionController(service,provider,Clock.fixed(Instant.parse("2026-11-15T00:00:00Z"),ZoneOffset.UTC),new tools.jackson.databind.ObjectMapper());
    @Test void privateApiRejectsMissingAdminOrUnacceptedTermsPrincipals() {
        assertThatThrownBy(() -> controller.me(null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> controller.me(new CurrentUser(1L,"SUPER_ADMIN",true))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> controller.me(new CurrentUser(1L,"USER",false))).isInstanceOf(ApiException.class);
        verifyNoInteractions(service);
    }
    @Test void ordinaryUserCannotOverrideAndInternalGrantRequiresService() {
        var user=new CurrentUser(1L,"USER",true);
        assertThatThrownBy(() -> controller.override(user,42L,new SubscriptionController.OverrideRequest("FREE",null,"support"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> controller.entitlements(user,42L)).isInstanceOf(ApiException.class);
        controller.entitlements(new CurrentUser(-1L,"SERVICE",true),42L);
        verify(service).entitlements(42L);
    }
    @Test void invalidWebhookNeverReachesStateMutation() {
        assertThatThrownBy(() -> controller.webhook("{}","bad")).isInstanceOf(ApiException.class);
        verifyNoInteractions(service);
    }
}
