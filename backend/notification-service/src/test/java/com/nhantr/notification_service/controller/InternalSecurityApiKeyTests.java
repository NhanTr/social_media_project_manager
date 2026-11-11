package com.nhantr.notification_service.controller;

import com.nhantr.common.security.BearerTokenFilter;
import com.nhantr.common.security.CurrentUser;
import com.nhantr.common.security.JwtTokenService;
import com.nhantr.notification_service.delivery.NotificationDeliveryService;
import com.nhantr.notification_service.email.EmailDeliveryResult;
import com.nhantr.notification_service.email.EmailSendRequest;
import com.nhantr.notification_service.email.EmailTemplateService;
import com.nhantr.notification_service.email.EmailTemplateType;
import com.nhantr.notification_service.email.MockEmailAdapter;
import com.nhantr.notification_service.entity.DeviceToken;
import com.nhantr.notification_service.entity.Notification;
import com.nhantr.notification_service.provider.MockPushNotificationProvider;
import com.nhantr.notification_service.service.NotificationService;
import com.nhantr.notification_service.testutil.TestNotificationRepositories.FakeDeviceTokenRepository;
import com.nhantr.notification_service.testutil.TestNotificationRepositories.FakeNotificationDeliveryRepository;
import com.nhantr.notification_service.testutil.TestNotificationRepositories.FakeNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InternalSecurityApiKeyTests {

    private static final String VALID_KEY = "test-internal-api-key-xyz-777";

    private BearerTokenFilter filter;
    private InternalNotificationController internalController;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        JwtTokenService jwtTokenService = new JwtTokenService("12345678901234567890123456789012");
        filter = new BearerTokenFilter(jwtTokenService, VALID_KEY);

        var notificationRepo = new FakeNotificationRepository();
        var tokenRepo = new FakeDeviceTokenRepository();
        var pushProvider = new MockPushNotificationProvider();
        var deliveryRepo = new FakeNotificationDeliveryRepository();
        var deliveryService = new NotificationDeliveryService(deliveryRepo, pushProvider);
        var emailAdapter = new MockEmailAdapter(new EmailTemplateService());

        tokenRepo.save(new DeviceToken(400L, "token-user-400", DeviceToken.Platform.IOS));

        service = new NotificationService(notificationRepo, tokenRepo, pushProvider, emailAdapter, deliveryService);
        internalController = new InternalNotificationController(service);
    }

    @Test
    void filterGrantsServiceRoleWhenApiKeyIsValid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/v1/notifications/new-post");
        request.addHeader("X-Internal-Api-Key", VALID_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_SERVICE"));
        assertThat(((CurrentUser) auth.getPrincipal()).role()).isEqualTo("SERVICE");
    }

    @Test
    void filterDeniesServiceRoleWhenApiKeyIsMissingOrInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/v1/notifications/new-post");
        request.addHeader("X-Internal-Api-Key", "wrong-invalid-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull();
    }

    @Test
    void internalEndpointsDisableUserTokensAndSendEmail() {
        // Test disable user tokens (logout)
        var disableResult = internalController.disableUserTokens(400L);
        assertThat(disableResult.userId()).isEqualTo(400L);
        assertThat(disableResult.disabledTokens()).isEqualTo(1);

        // Test send email
        EmailSendRequest emailReq = new EmailSendRequest(
                "user400@example.com",
                EmailTemplateType.SUBSCRIPTION_ACTIVATED,
                Map.of("username", "Tuấn Dũng", "plan", "Locket Gold"),
                "email-idem-1"
        );
        EmailDeliveryResult emailResult = internalController.sendEmail(emailReq);
        assertThat(emailResult.success()).isTrue();
        assertThat(emailResult.messageId()).isNotNull();

        // Test friend-request creation
        var notif = internalController.friendRequest(
                "key-fr-1",
                new NotificationService.NotificationEvent(400L, 102L, null, "Connect", "Friend request")
        );
        assertThat(notif).isNotNull();
        assertThat(notif.type()).isEqualTo(Notification.Type.FRIEND_REQUEST.name());
    }
}

