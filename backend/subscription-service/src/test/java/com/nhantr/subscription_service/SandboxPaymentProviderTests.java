package com.nhantr.subscription_service;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class SandboxPaymentProviderTests {
    @Test void rawBodyTimestampAndSignatureMustAllMatch() {
        var provider = new SandboxPaymentProvider("https://sandbox.example/checkouts", "a-test-key-with-more-than-32-bytes");
        var now = Instant.parse("2026-11-15T00:00:00Z");
        var body="{\"eventId\":\"one\"}";
        String signature=provider.sign(body,now);
        provider.verify(body,signature,now);
        assertThatThrownBy(() -> provider.verify(body+" ",signature,now)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> provider.verify(body,signature,now.plusSeconds(301))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> provider.verify(body,null,now)).isInstanceOf(RuntimeException.class);
        assertThat(provider.checkoutUrl("abc")).isEqualTo("https://sandbox.example/checkouts/abc");
        assertThatThrownBy(() -> new SandboxPaymentProvider("http://unsafe.example", "key")).isInstanceOf(IllegalArgumentException.class);
    }
}
