package com.nhantr.notification_service.email;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class EmailTemplateService {

    public RenderedEmail render(EmailTemplateType type, Map<String, Object> variables) {
        String username = variables != null && variables.containsKey("username")
                ? String.valueOf(variables.get("username")) : "Friend";

        return switch (type) {
            case AUTH_WELCOME -> new RenderedEmail(
                    "Welcome to Locket!",
                    "Hi " + username + ",\nWelcome to Locket! Pin your widget and share real-time moments with your best friends.",
                    "<h1>Welcome to Locket!</h1><p>Hi <b>" + username + "</b>,</p><p>Pin your widget to start sharing photos right to your friends' home screens!</p>"
            );
            case AUTH_OTP_ALERT -> {
                String otp = variables != null && variables.containsKey("otp")
                        ? String.valueOf(variables.get("otp")) : "------";
                yield new RenderedEmail(
                        "Your Locket Verification Code",
                        "Hi " + username + ",\nYour verification code is: " + otp + ". Do not share this code with anyone.",
                        "<h2>Security Alert</h2><p>Hi " + username + ",</p><p>Your verification code is: <b>" + otp + "</b></p><p>Do not share this code.</p>"
                );
            }
            case SUBSCRIPTION_ACTIVATED -> {
                String plan = variables != null && variables.containsKey("plan")
                        ? String.valueOf(variables.get("plan")) : "Locket Gold";
                yield new RenderedEmail(
                        "Locket Gold Activated!",
                        "Hi " + username + ",\nYour subscription to " + plan + " is now active! Enjoy unlimited friends and extended video uploads.",
                        "<h1>Locket Gold Activated</h1><p>Hi " + username + ",</p><p>Your <b>" + plan + "</b> is now active! Enjoy premium features.</p>"
                );
            }
            case SUBSCRIPTION_CANCELLED -> new RenderedEmail(
                    "Locket Subscription Cancelled",
                    "Hi " + username + ",\nYour subscription has been cancelled. You can continue using benefits until the end of your billing cycle.",
                    "<h2>Subscription Notice</h2><p>Hi " + username + ",</p><p>Your subscription has been cancelled. We hope to see you back soon!</p>"
            );
        };
    }
}

