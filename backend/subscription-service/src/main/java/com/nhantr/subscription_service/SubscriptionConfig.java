package com.nhantr.subscription_service;
import com.nhantr.common.security.BearerTokenFilter;
import com.nhantr.common.security.JwtTokenService;
import com.nhantr.common.security.SecuritySupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
@Configuration
public class SubscriptionConfig {
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean TransactionTemplate transactions(PlatformTransactionManager manager) { return new TransactionTemplate(manager); }
    @Bean SandboxPaymentProvider sandboxPaymentProvider(@Value("${app.payment.checkout-base-url}") String base,
            @Value("${app.payment.webhook-secret}") String secret,
            @Value("${app.payment.sandbox-enabled:false}") boolean enabled) { return new SandboxPaymentProvider(base,secret,enabled); }
    @Bean JwtTokenService jwtTokenService(@Value("${app.jwt.secret}") String secret) { return new JwtTokenService(secret); }
    @Bean SecurityFilterChain security(HttpSecurity http,JwtTokenService jwt,@Value("${app.internal-api-key}") String key) throws Exception {
        return SecuritySupport.serviceChain(http,new BearerTokenFilter(jwt,key),
                "/api/v1/payments/webhook","/api/v1/payments/sandbox/checkouts/**");
    }
}
